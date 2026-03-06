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
 * Controller para integração com WhatsApp via webhook.
 * Endpoints públicos (sem autenticação JWT) para receber mensagens da WhatsApp Cloud API.
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
     * Verificação do webhook (GET) - exigida pela Meta ao configurar o webhook.
     * Retorna hub.challenge se hub.verify_token for válido.
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
     * Recebe mensagens do WhatsApp (POST).
     * Processa em background e retorna 200 imediatamente para evitar timeout.
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

                    if ("text".equals(msg.type) && msg.text != null && msg.text.body != null && !msg.text.body.isBlank()) {
                        String prompt = msg.text.body.trim();
                        Log.info("WhatsApp mensagem de " + from + ": " + prompt);
                        processAndReplyAsync(sessionId, from, phoneNumberId, prompt);
                    } else if ("audio".equals(msg.type) && msg.audio != null && msg.audio.id != null) {
                        Log.info("WhatsApp áudio de " + from + " (media id: " + msg.audio.id + ")");
                        processAudioAndReplyAsync(msg.audio.id, msg.audio.mimeType, from, phoneNumberId);
                    }
                }
            }
        }

        return Response.ok().build();
    }

    private void processAudioAndReplyAsync(String mediaId, String mimeType, String from, String phoneNumberIdFromWebhook) {
        BlockingToReactive.wrap(() -> {
            Optional<byte[]> audioData = whatsAppMediaDownloader.downloadMedia(mediaId);
            return audioData.flatMap(data -> speechToTextService.transcribe(data, mimeType));
        })
                .onItem().invoke(transcribedOpt -> {
                    if (transcribedOpt.isEmpty()) {
                        whatsAppMessageSender.sendTextMessage(from,
                                "Não foi possível transcrever o áudio. Verifique se o OpenAI está configurado e tente enviar uma mensagem de texto.",
                                phoneNumberIdFromWebhook);
                    } else {
                        String sessionId = "whatsapp:" + from;
                        processAndReplyAsync(sessionId, from, phoneNumberIdFromWebhook, transcribedOpt.get());
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Erro ao processar áudio WhatsApp de " + from, e);
                    whatsAppMessageSender.sendTextMessage(from,
                            "Desculpe, ocorreu um erro ao processar seu áudio. Tente novamente ou envie uma mensagem de texto.",
                            phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("Áudio WhatsApp processado para " + from),
                        e -> Log.error("Falha no processamento de áudio WhatsApp", e)
                );
    }

    private void processAndReplyAsync(String sessionId, String to, String phoneNumberIdFromWebhook, String prompt) {
        chatbotUseCase.executeWithPhone(sessionId, prompt, to)
                .collect().asList()
                .onItem().transform(list -> String.join("", list))
                .onItem().invoke(response -> {
                    boolean sent = whatsAppMessageSender.sendTextMessage(to, response, phoneNumberIdFromWebhook);
                    if (!sent) {
                        Log.warn("Falha ao enviar resposta WhatsApp para " + to + " - verifique whatsapp.phone-number-id e whatsapp.access-token");
                    }
                })
                .onFailure().invoke(e -> {
                    Log.error("Erro ao processar mensagem WhatsApp de " + to, e);
                    String errorMsg = "Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente.";
                    whatsAppMessageSender.sendTextMessage(to, errorMsg, phoneNumberIdFromWebhook);
                })
                .subscribe().with(
                        r -> Log.debug("Resposta WhatsApp enviada para " + to),
                        e -> Log.error("Falha no processamento WhatsApp", e)
                );
    }
}
