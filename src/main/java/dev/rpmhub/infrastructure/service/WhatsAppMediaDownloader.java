/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Serviço para baixar mídia (áudio, etc.) da WhatsApp Cloud API.
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media
 */
@ApplicationScoped
public class WhatsAppMediaDownloader {

    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v21.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "whatsapp.access-token", defaultValue = "")
    String accessToken;

    public WhatsAppMediaDownloader(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Baixa o conteúdo de uma mídia pelo ID retornado no webhook.
     *
     * @param mediaId ID da mídia (ex: do webhook audio.id)
     * @return Optional com os bytes do arquivo, ou empty se falhar
     */
    public Optional<byte[]> downloadMedia(String mediaId) {
        if (accessToken == null || accessToken.isBlank()) {
            Log.warn("WhatsApp access-token ausente - não é possível baixar mídia");
            return Optional.empty();
        }
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }

        try {
            // 1. Obter URL da mídia
            HttpRequest metaRequest = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + mediaId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> metaResponse = httpClient.send(metaRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (metaResponse.statusCode() != 200) {
                Log.error("Falha ao obter URL da mídia WhatsApp: " + metaResponse.statusCode() + " - " + metaResponse.body());
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(metaResponse.body());
            String url = json.has("url") ? json.get("url").asText() : null;
            if (url == null || url.isBlank()) {
                Log.error("Resposta da mídia WhatsApp sem URL");
                return Optional.empty();
            }

            // 2. Baixar o arquivo
            HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<byte[]> downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (downloadResponse.statusCode() != 200) {
                Log.error("Falha ao baixar mídia WhatsApp: " + downloadResponse.statusCode());
                return Optional.empty();
            }

            byte[] data = downloadResponse.body();
            if (data == null || data.length == 0) {
                Log.warn("Mídia WhatsApp vazia");
                return Optional.empty();
            }

            Log.info("Mídia WhatsApp baixada: " + data.length + " bytes");
            return Optional.of(data);
        } catch (Exception e) {
            Log.error("Erro ao baixar mídia WhatsApp", e);
            return Optional.empty();
        }
    }
}
