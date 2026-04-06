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

package dev.orion.rag.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.rag.domain.port.out.MediaDownloaderPort;
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
 * Implementation of {@link MediaDownloaderPort} using the WhatsApp Cloud API (Meta).
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media
 */
@ApplicationScoped
public class WhatsAppMediaDownloaderAdapter implements MediaDownloaderPort {

    /** Base URL of the WhatsApp Graph API. */
    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v21.0";

    /** Blocking HTTP client for the two-step media download (metadata → binary). */
    private final HttpClient httpClient;
    /** Jackson mapper for parsing the JSON metadata response from the Graph API. */
    private final ObjectMapper objectMapper;

    /** WhatsApp Cloud API access token used to authenticate all requests. */
    @ConfigProperty(name = "whatsapp.access-token")
    Optional<String> accessToken;

    /**
     * Creates a WhatsAppMediaDownloaderAdapter; initialises the HTTP client with a 10-second connect timeout.
     *
     * @param objectMapper Jackson mapper for JSON parsing
     */
    public WhatsAppMediaDownloaderAdapter(ObjectMapper objectMapper) {
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
    @Override
    public Optional<byte[]> downloadMedia(String mediaId) {
        String token = accessToken.orElse("");
        if (token.isBlank()) {
            Log.warn("WhatsApp access-token ausente - não é possível baixar mídia");
            return Optional.empty();
        }
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest metaRequest = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + mediaId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> metaResponse = httpClient.send(metaRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (metaResponse.statusCode() != 200) {
                Log.error("Falha ao obter URL da mídia WhatsApp: "
                        + metaResponse.statusCode() + " - " + metaResponse.body());
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(metaResponse.body());
            String url = json.has("url") ? json.get("url").asText() : null;
            if (url == null || url.isBlank()) {
                Log.error("Resposta da mídia WhatsApp sem URL");
                return Optional.empty();
            }

            HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<byte[]> downloadResponse = httpClient.send(downloadRequest,
                    HttpResponse.BodyHandlers.ofByteArray());
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
