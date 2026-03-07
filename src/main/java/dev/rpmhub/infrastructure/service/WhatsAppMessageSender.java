/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Serviço para enviar mensagens via WhatsApp Cloud API (Meta).
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages
 */
@ApplicationScoped
public class WhatsAppMessageSender {

    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v21.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "whatsapp.phone-number-id", defaultValue = "")
    String phoneNumberId;

    @ConfigProperty(name = "whatsapp.access-token", defaultValue = "")
    String accessToken;

    public WhatsAppMessageSender(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Envia uma mensagem de texto para um número WhatsApp.
     *
     * @param to   Número no formato internacional sem + (ex: 5511999999999)
     * @param text Conteúdo da mensagem
     * @return true se enviado com sucesso, false caso contrário
     */
    public boolean sendTextMessage(String to, String text) {
        return sendTextMessage(to, text, null);
    }

    /**
     * Envia uma mensagem de texto para um número WhatsApp.
     *
     * @param to            Número no formato internacional sem + (ex: 5511999999999)
     * @param text          Conteúdo da mensagem
     * @param overridePhoneId ID do número de negócio (do webhook). Se null, usa o configurado.
     * @return true se enviado com sucesso, false caso contrário
     */
    public boolean sendTextMessage(String to, String text, String overridePhoneId) {
        String effectivePhoneId = (overridePhoneId != null && !overridePhoneId.isBlank()) ? overridePhoneId : phoneNumberId;
        if (effectivePhoneId == null || effectivePhoneId.isBlank() || accessToken == null || accessToken.isBlank()) {
            Log.warn("WhatsApp não configurado: phone-number-id ou access-token ausente");
            return false;
        }
        if (to == null || to.isBlank() || text == null || text.isBlank()) {
            Log.warn("Parâmetros inválidos para envio WhatsApp: to ou text vazio");
            return false;
        }

        String normalizedTo = normalizeBrazilianMobile(to.replaceAll("[^0-9]", ""));
        Log.info("WhatsApp enviando para número: [" + normalizedTo + "] (phone-number-id: " + effectivePhoneId + ")");

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", normalizedTo);
            body.put("type", "text");
            body.putObject("text").put("body", text);

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + effectivePhoneId + "/messages"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Log.info("Mensagem WhatsApp enviada com sucesso para " + to);
                return true;
            } else {
                Log.error("Falha ao enviar mensagem WhatsApp: " + response.statusCode() + " - " + response.body());
                return false;
            }
        } catch (Exception e) {
            Log.error("Erro ao enviar mensagem WhatsApp", e);
            return false;
        }
    }

    /**
     * Sends a read receipt and typing indicator to a WhatsApp user.
     * The read receipt shows double blue checkmarks; the typing indicator shows the "..." animation.
     *
     * @param to            Recipient phone number (international format without +)
     * @param messageId     The WAMID of the received message to mark as read
     * @param overridePhoneId Business phone number ID from the webhook (or null to use configured)
     * @return true if at least the read receipt was sent successfully
     */
    public boolean sendTypingIndicator(String to, String messageId, String overridePhoneId) {
        String effectivePhoneId = (overridePhoneId != null && !overridePhoneId.isBlank()) ? overridePhoneId : phoneNumberId;
        if (effectivePhoneId == null || effectivePhoneId.isBlank() || accessToken == null || accessToken.isBlank()) {
            Log.warn("WhatsApp não configurado: phone-number-id ou access-token ausente — typing indicator ignorado");
            return false;
        }
        if (messageId == null || messageId.isBlank()) {
            Log.warn("messageId ausente — typing indicator ignorado");
            return false;
        }

        String normalizedTo = normalizeBrazilianMobile(to != null ? to.replaceAll("[^0-9]", "") : "");

        // Mark message as read (shows double blue checkmarks to the user)
        boolean readReceiptSent = sendReadReceipt(effectivePhoneId, messageId);

        // Send typing indicator (animated "..." dots) — requires "to" field, different from status update
        boolean typingSent = sendTypingAction(effectivePhoneId, normalizedTo, messageId);

        Log.info("WhatsApp feedback enviado para " + to + " — read receipt: " + readReceiptSent + ", typing: " + typingSent);
        return readReceiptSent || typingSent;
    }

    private boolean sendReadReceipt(String effectivePhoneId, String messageId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("status", "read");
            body.put("message_id", messageId);

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + effectivePhoneId + "/messages"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Log.info("WhatsApp read receipt enviado para mensagem " + messageId);
                return true;
            } else {
                Log.warn("WhatsApp read receipt falhou: " + response.statusCode() + " - " + response.body());
                return false;
            }
        } catch (Exception e) {
            Log.warn("Erro ao enviar read receipt: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends an emoji reaction to a WhatsApp message to signal that processing has started.
     * The Cloud API does not support a native typing indicator ("..."), so a reaction emoji
     * is the closest equivalent — it appears instantly and can be cleared after the reply is sent.
     *
     * @param to      Recipient phone number (normalized, no +)
     * @param messageId The WAMID to react to
     * @param emoji   The emoji to send (e.g. "⏳"). Pass "" to clear the reaction.
     * @param overridePhoneId Business phone number ID (or null to use configured)
     * @return true if the reaction was sent successfully
     */
    public boolean sendReaction(String to, String messageId, String emoji, String overridePhoneId) {
        String effectivePhoneId = (overridePhoneId != null && !overridePhoneId.isBlank()) ? overridePhoneId : phoneNumberId;
        if (effectivePhoneId == null || effectivePhoneId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        if (to == null || to.isBlank() || messageId == null || messageId.isBlank()) {
            return false;
        }

        String normalizedTo = normalizeBrazilianMobile(to.replaceAll("[^0-9]", ""));

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", normalizedTo);
            body.put("type", "reaction");
            body.putObject("reaction")
                    .put("message_id", messageId)
                    .put("emoji", emoji);

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + effectivePhoneId + "/messages"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Log.info("WhatsApp reaction '" + emoji + "' enviada para mensagem " + messageId);
                return true;
            } else {
                Log.warn("WhatsApp reaction falhou: " + response.statusCode() + " - " + response.body());
                return false;
            }
        } catch (Exception e) {
            Log.warn("Erro ao enviar reaction: " + e.getMessage());
            return false;
        }
    }

    private boolean sendTypingAction(String effectivePhoneId, String to, String messageId) {
        return sendReaction(to, messageId, "⏳", effectivePhoneId);
    }

    /**
     * Normalizes Brazilian mobile numbers by adding the 9th digit when missing.
     * WhatsApp Cloud API sometimes delivers BR numbers in the old 8-digit format
     * (55 + XX + 8 digits) while the allowed list uses the 9-digit format
     * (55 + XX + 9XXXXXXXX).
     */
    static String normalizeBrazilianMobile(String number) {
        if (number != null && number.matches("^55\\d{2}[6-9]\\d{7}$")) {
            return number.substring(0, 4) + "9" + number.substring(4);
        }
        return number;
    }
}
