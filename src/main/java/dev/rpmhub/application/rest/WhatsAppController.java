/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest;

import dev.rpmhub.application.rest.dto.WhatsAppWebhookPayload;
import dev.rpmhub.domain.usecase.ChatbotUseCase;
import dev.rpmhub.infrastructure.service.SpeechToTextService;
import dev.rpmhub.infrastructure.service.WhatsAppMediaDownloader;
import dev.rpmhub.infrastructure.service.WhatsAppMessageSender;
import dev.rpmhub.infrastructure.util.BlockingToReactive;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Controller for WhatsApp integration via webhook.
 * Public endpoints (no JWT authentication) to receive messages from WhatsApp Cloud API.
 * 
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks">WhatsApp Webhooks</a>
 */
@Path("/webhook/whatsapp")
@Produces(MediaType.TEXT_PLAIN)
public class WhatsAppController {

    private final ChatbotUseCase chatbotUseCase;
    private final WhatsAppMessageSender whatsAppMessageSender;
    private final WhatsAppMediaDownloader whatsAppMediaDownloader;
    private final SpeechToTextService speechToTextService;

    @ConfigProperty(name = "whatsapp.verify-token", defaultValue = "rag-webhook-verify")
    String verifyToken;

    @Inject
    public WhatsAppController(ChatbotUseCase chatbotUseCase, WhatsAppMessageSender whatsAppMessageSender,
            WhatsAppMediaDownloader whatsAppMediaDownloader, SpeechToTextService speechToTextService) {
        this.chatbotUseCase = chatbotUseCase;
        this.whatsAppMessageSender = whatsAppMessageSender;
        this.whatsAppMediaDownloader = whatsAppMediaDownloader;
        this.speechToTextService = speechToTextService;
    }

    /**
     * Webhook verification (GET) - required by Meta when configuring the webhook.
     * Returns hub.challenge if hub.verify_token is valid.
     */
    @GET
    public Response verifyWebhook(
            @QueryParam("hub.mode") String mode,
            @QueryParam("hub.verify_token") String token,
            @QueryParam("hub.challenge") String challenge) {
        Log.info("WhatsApp webhook verification: mode=" + mode + ", token=" + (token != null ? "***" : "null"));

        if ("subscribe".equals(mode) && verifyToken.equals(token) && challenge != null) {
            return Response.ok(challenge).build();
        }
        return Response.status(403).build();
    }

    /**
     * Receives WhatsApp messages (POST).
     * Processes in background and returns 200 immediately to avoid timeout.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receiveWebhook(WhatsAppWebhookPayload payload) {
        if (payload == null || payload.entry == null || payload.entry.isEmpty()) {
            return Response.ok().build();
        }

        for (WhatsAppWebhookPayload.WebhookEntry entry : payload.entry) {
            if (entry.changes == null) continue;
            for (WhatsAppWebhookPayload.WebhookChange change : entry.changes) {
                if (change.value == null || change.value.messages == null) continue;

                String phoneNumberId = change.value.metadata != null ? change.value.metadata.phoneNumberId : null;

                for (WhatsAppWebhookPayload.WebhookMessage msg : change.value.messages) {
                    String from = msg.from;
                    String sessionId = "whatsapp:" + from;
                    String messageId = msg.id;

                    if ("text".equals(msg.type) && msg.text != null && msg.text.body != null && !msg.text.body.isBlank()) {
                        String prompt = msg.text.body.trim();
                        Log.info("WhatsApp message from " + from + ": " + prompt);
                        whatsAppMessageSender.sendTypingIndicator(from, messageId, phoneNumberId);
                        processAndReplyAsync(sessionId, from, phoneNumberId, prompt, messageId);
                    } else if ("audio".equals(msg.type) && msg.audio != null && msg.audio.id != null) {
                        Log.info("WhatsApp audio from " + from + " (media id: " + msg.audio.id + ")");
                        whatsAppMessageSender.sendTypingIndicator(from, messageId, phoneNumberId);
                        processAudioAndReplyAsync(msg.audio.id, msg.audio.mimeType, from, phoneNumberId, messageId);
                    }
                }
            }
        }

        return Response.ok().build();
    }

    private void processAudioAndReplyAsync(String mediaId, String mimeType, String from, String phoneNumberIdFromWebhook, String messageId) {
        BlockingToReactive.wrap(() -> {
            Optional<byte[]> audioData = whatsAppMediaDownloader.downloadMedia(mediaId);
            return audioData.flatMap(data -> speechToTextService.transcribe(data, mimeType));
        })
                .onItem().invoke(transcribedOpt -> {
                    if (transcribedOpt.isEmpty()) {
                        whatsAppMessageSender.sendReaction(from, messageId, "", phoneNumberIdFromWebhook);
                        whatsAppMessageSender.sendTextMessage(from,
                                "Could not transcribe the audio. Please verify OpenAI is configured and try sending a text message.",
                                phoneNumberIdFromWebhook);
                    } else {
                        String sessionId = "whatsapp:" + from;
                        processAndReplyAsync(sessionId, from, phoneNumberIdFromWebhook, transcribedOpt.get(), messageId);
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Error processing WhatsApp audio from " + from, e);
                    whatsAppMessageSender.sendReaction(from, messageId, "", phoneNumberIdFromWebhook);
                    whatsAppMessageSender.sendTextMessage(from,
                            "Sorry, an error occurred while processing your audio. Please try again or send a text message.",
                            phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("WhatsApp audio processed for " + from),
                        e -> Log.error("WhatsApp audio processing failure", e)
                );
    }

    private void processAndReplyAsync(String sessionId, String to, String phoneNumberIdFromWebhook, String prompt, String messageId) {
        chatbotUseCase.executeWithPhone(sessionId, prompt, to)
                .collect().asList()
                .onItem().transform(list -> String.join("", list))
                .onItem().invoke(response -> {
                    // Clear the ⏳ reaction before sending the reply
                    whatsAppMessageSender.sendReaction(to, messageId, "", phoneNumberIdFromWebhook);
                    boolean sent = whatsAppMessageSender.sendTextMessage(to, response, phoneNumberIdFromWebhook);
                    if (!sent) {
                        Log.warn("Failed to send WhatsApp response to " + to + " - check whatsapp.phone-number-id and whatsapp.access-token");
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Error processing WhatsApp message from " + to, e);
                    whatsAppMessageSender.sendReaction(to, messageId, "", phoneNumberIdFromWebhook);
                    whatsAppMessageSender.sendTextMessage(to, "Sorry, an error occurred while processing your message. Please try again.", phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("WhatsApp response sent to " + to),
                        e -> Log.error("WhatsApp processing failure", e)
                );
    }
}
