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
import dev.rpmhub.infrastructure.service.WhatsAppMessageSender;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @ConfigProperty(name = "whatsapp.verify-token", defaultValue = "rag-webhook-verify")
    String verifyToken;

    @Inject
    public WhatsAppController(ChatbotUseCase chatbotUseCase, WhatsAppMessageSender whatsAppMessageSender) {
        this.chatbotUseCase = chatbotUseCase;
        this.whatsAppMessageSender = whatsAppMessageSender;
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
                    if (!"text".equals(msg.type) || msg.text == null || msg.text.body == null || msg.text.body.isBlank()) {
                        continue;
                    }
                    String from = msg.from;
                    String prompt = msg.text.body.trim();

                    String sessionId = "whatsapp:" + from;
                    Log.info("WhatsApp mensagem de " + from + ": " + prompt);

                    processAndReplyAsync(sessionId, from, phoneNumberId, prompt);
                }
            }
        }

        return Response.ok().build();
    }

    private void processAndReplyAsync(String sessionId, String to, String phoneNumberIdFromWebhook, String prompt) {
        chatbotUseCase.execute(sessionId, prompt)
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
                    whatsAppMessageSender.sendTextMessage(to, errorMsg);
                })
                .subscribe().with(
                        r -> Log.debug("Resposta WhatsApp enviada para " + to),
                        e -> Log.error("Falha no processamento WhatsApp", e)
                );
    }
}
