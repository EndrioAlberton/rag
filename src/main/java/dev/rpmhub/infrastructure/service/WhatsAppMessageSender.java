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

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", to.replaceAll("[^0-9]", ""));
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
}
