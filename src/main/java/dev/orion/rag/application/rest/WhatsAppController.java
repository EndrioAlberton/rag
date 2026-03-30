/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.rag.application.rest;

import dev.orion.rag.domain.port.out.MediaDownloaderPort;
import dev.orion.rag.domain.port.out.MessageSenderPort;
import dev.orion.rag.domain.port.out.SpeechToTextPort;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.infrastructure.BlockingToReactive;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

    /** Chatbot port used to generate the AI reply for each incoming message. */
    private final ChatbotPort chatbotUseCase;
    /** Port used to send text messages, typing indicators and reactions via WhatsApp. */
    private final MessageSenderPort messageSender;
    /** Port used to download media (e.g. audio notes) from the WhatsApp media server. */
    private final MediaDownloaderPort mediaDownloader;
    /** Port used to transcribe audio data to text before passing it to the chatbot. */
    private final SpeechToTextPort speechToText;

    /** Token expected in the {@code hub.verify_token} query parameter during webhook setup. */
    @ConfigProperty(name = "whatsapp.verify-token", defaultValue =
        "rag-webhook-verify")
    String verifyToken;

    /**
     * Constructs the controller with all required ports.
     *
     * @param chatbotUseCase   chatbot port for generating AI replies
     * @param messageSender    port for sending messages via WhatsApp
     * @param mediaDownloader  port for downloading media attachments
     * @param speechToText     port for transcribing audio to text
     */
    @Inject
    public WhatsAppController(ChatbotPort chatbotUseCase, MessageSenderPort
        messageSender,
            MediaDownloaderPort mediaDownloader, SpeechToTextPort speechToText)
                {
        this.chatbotUseCase = chatbotUseCase;
        this.messageSender = messageSender;
        this.mediaDownloader = mediaDownloader;
        this.speechToText = speechToText;
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
        Log.info("WhatsApp webhook verification: mode=" + mode + ", token=" +
            (token != null ? "***" : "null"));

        if ("subscribe".equals(mode) && verifyToken.equals(token) && challenge
            != null) {
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
        if (payload == null || payload.entry == null || payload.entry.isEmpty())
            {
            return Response.ok().build();
        }

        for (WhatsAppWebhookPayload.WebhookEntry entry : payload.entry) {
            if (entry.changes == null) {
                continue;
            }
            for (WhatsAppWebhookPayload.WebhookChange change : entry.changes) {
                if (change.value == null || change.value.messages == null) {
                    continue;
                }

                String phoneNumberId = change.value.metadata != null ?
                    change.value.metadata.phoneNumberId : null;

                for (WhatsAppWebhookPayload.WebhookMessage msg :
                    change.value.messages) {
                    String from = msg.from;
                    String sessionId = "whatsapp:" + from;
                    String messageId = msg.id;
                    String senderName =
                        resolveProfileName(change.value.contacts, from);

                    if ("text".equals(msg.type) && msg.text != null &&
                        msg.text.body != null && !msg.text.body.isBlank()) {
                        String prompt = msg.text.body.trim();
                        Log.info("WhatsApp message from " + from + ": " +
                            prompt);
                        messageSender.sendTypingIndicator(from, messageId,
                            phoneNumberId);
                        processAndReplyAsync(sessionId, from, phoneNumberId,
                            prompt, messageId, senderName);
                    } else if ("audio".equals(msg.type) && msg.audio != null &&
                        msg.audio.id != null) {
                        Log.info("WhatsApp audio from " + from + " (media id: "
                            + msg.audio.id + ")");
                        messageSender.sendTypingIndicator(from, messageId,
                            phoneNumberId);
                        processAudioAndReplyAsync(msg.audio.id,
                            msg.audio.mimeType, from, phoneNumberId, messageId,
                            senderName);
                    }
                }
            }
        }

        return Response.ok().build();
    }

    /**
     * Downloads, transcribes and processes a WhatsApp audio message asynchronously.
     *
     * @param mediaId                  WhatsApp media identifier
     * @param mimeType                 MIME type of the audio file
     * @param from                     sender's WhatsApp number
     * @param phoneNumberIdFromWebhook business phone number ID from the webhook metadata
     * @param messageId                identifier of the incoming message (for reactions)
     * @param senderName               display name of the sender (may be null)
     */
    private void processAudioAndReplyAsync(String mediaId, String mimeType,
        String from,
            String phoneNumberIdFromWebhook, String messageId, String
                senderName) {
        BlockingToReactive.wrap(() -> {
            Optional<byte[]> audioData = mediaDownloader.downloadMedia(mediaId);
            return audioData.flatMap(data -> speechToText.transcribe(data,
                mimeType));
        })
                .onItem().invoke(transcribedOpt -> {
                    if (transcribedOpt.isEmpty()) {
                        messageSender.sendReaction(from, messageId, "",
                            phoneNumberIdFromWebhook);
                        messageSender.sendTextMessage(
                                from,
                                "Could not transcribe the audio. Please verify OpenAI is "
                                        + "configured and try sending a text message.",
                                phoneNumberIdFromWebhook);
                    } else {
                        String sessionId = "whatsapp:" + from;
                        processAndReplyAsync(sessionId, from,
                            phoneNumberIdFromWebhook, transcribedOpt.get(),
                            messageId, senderName);
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Error processing WhatsApp audio from " + from,
                        e);
                    messageSender.sendReaction(from, messageId, "",
                        phoneNumberIdFromWebhook);
                    messageSender.sendTextMessage(
                            from,
                            "Sorry, an error occurred while processing your audio. Please try "
                                    + "again or send a text message.",
                            phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("WhatsApp audio processed for " + from),
                        e -> Log.error("WhatsApp audio processing failure", e)
                );
    }

    /**
     * Calls the chatbot use case and sends the complete streamed response back via WhatsApp.
     *
     * @param sessionId                session identifier (prefixed with {@code whatsapp:})
     * @param to                       recipient's WhatsApp number
     * @param phoneNumberIdFromWebhook business phone number ID from the webhook metadata
     * @param prompt                   text prompt to pass to the chatbot
     * @param messageId                identifier of the incoming message (for reactions)
     * @param senderName               display name of the sender (may be null)
     */
    private void processAndReplyAsync(String sessionId, String to, String
        phoneNumberIdFromWebhook,
            String prompt, String messageId, String senderName) {
        Multi.createFrom().publisher(chatbotUseCase.executeWithPhone(sessionId,
            prompt, to, senderName, null))
                .collect().asList()
                .onItem().transform(list -> String.join("", list))
                .onItem().invoke(response -> {
                    messageSender.sendReaction(to, messageId, "",
                        phoneNumberIdFromWebhook);
                    boolean sent = messageSender.sendTextMessage(to, response,
                        phoneNumberIdFromWebhook);
                    if (!sent) {
                        Log.warn(
                                "Failed to send WhatsApp response to "
                                        + to
                                        + " - check whatsapp.phone-number-id and "
                                        + "whatsapp.access-token");
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Error processing WhatsApp message from " + to,
                        e);
                    messageSender.sendReaction(to, messageId, "",
                        phoneNumberIdFromWebhook);
                    messageSender.sendTextMessage(
                            to,
                            "Sorry, an error occurred while processing your message. Please try "
                                    + "again.",
                            phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("WhatsApp response sent to " + to),
                        e -> Log.error("WhatsApp processing failure", e)
                );
    }

    /**
     * Resolves the WhatsApp profile name for the given contact ID.
     *
     * @param contacts list of webhook contacts from the incoming payload
     * @param waId     WhatsApp contact identifier to look up
     * @return the profile display name, or {@code null} if not found
     */
    private String resolveProfileName(
            java.util.List<WhatsAppWebhookPayload.WebhookContact> contacts,
                String waId) {
        if (contacts == null) {
            return null;
        }
        return contacts.stream()
                .filter(c -> waId.equals(c.waId) && c.profile != null &&
                    c.profile.name != null
                        && !c.profile.name.isBlank())
                .map(c -> c.profile.name)
                .findFirst()
                .orElse(null);
    }
}
