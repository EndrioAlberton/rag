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
import dev.orion.rag.domain.port.out.SpeechToTextPort;
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
import java.util.UUID;

/**
 * Serviço de transcrição de áudio para texto usando OpenAI Whisper API.
 * https://platform.openai.com/docs/api-reference/audio/createTranscription
 */
@ApplicationScoped
public class SpeechToTextService implements SpeechToTextPort {

    /** Base URL of the OpenAI REST API. */
    private static final String OPENAI_API_BASE = "https://api.openai.com/v1";

    /** Blocking HTTP client used to call the Whisper transcription endpoint. */
    private final HttpClient httpClient;
    /** Jackson mapper for parsing the JSON response from OpenAI. */
    private final ObjectMapper objectMapper;

    /** OpenAI API key read from the Quarkus LangChain4j configuration. */
    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key", defaultValue = "")
    String apiKey;

    /**
     * Creates a SpeechToTextService; initialises the HTTP client with a 10-second connect timeout.
     *
     * @param objectMapper Jackson mapper for JSON response parsing
     */
    public SpeechToTextService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Transcreve áudio para texto usando OpenAI Whisper.
     *
     * @param audioData   Bytes do arquivo de áudio (ogg, mp3, wav, etc.)
     * @param mimeType    MIME type do áudio (ex: audio/ogg)
     * @return Optional com o texto transcrito, ou empty se falhar
     */
    public Optional<String> transcribe(byte[] audioData, String mimeType) {
        if (apiKey == null || apiKey.isBlank() || "change-me".equals(apiKey)) {
            Log.warn("OpenAI API key não configurada - transcrição de áudio indisponível");
            return Optional.empty();
        }
        if (audioData == null || audioData.length == 0) {
            return Optional.empty();
        }

        String extension = getExtensionFromMimeType(mimeType);
        String filename = "audio." + extension;

        try {
            String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] multipartBody = buildMultipartBody(boundary, audioData, filename, mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_BASE + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                Log.error("Falha na transcrição Whisper: " + response.statusCode() + " - " + response.body());
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String text = json.has("text") ? json.get("text").asText().trim() : null;
            if (text == null || text.isBlank()) {
                Log.warn("Transcrição Whisper retornou texto vazio");
                return Optional.empty();
            }

            Log.info("Áudio transcrito: " + text.length() + " caracteres");
            return Optional.of(text);
        } catch (Exception e) {
            Log.error("Erro na transcrição de áudio", e);
            return Optional.empty();
        }
    }

    /**
     * Derives the audio file extension from a MIME type string.
     *
     * @param mimeType MIME type (e.g. {@code audio/ogg; codecs=opus})
     * @return a short file extension such as {@code ogg}, {@code mp3}, {@code wav}, {@code webm} or {@code m4a}
     */
    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "ogg";
        }
        if (mimeType.contains("ogg") || mimeType.contains("opus")) {
            return "ogg";
        }
        if (mimeType.contains("mp3") || mimeType.contains("mpeg")) {
            return "mp3";
        }
        if (mimeType.contains("wav")) {
            return "wav";
        }
        if (mimeType.contains("webm")) {
            return "webm";
        }
        if (mimeType.contains("m4a") || mimeType.contains("mp4")) {
            return "m4a";
        }
        return "ogg";
    }

    /**
     * Builds the multipart/form-data body required by the OpenAI Whisper endpoint.
     *
     * @param boundary  MIME boundary string
     * @param audioData raw bytes of the audio file
     * @param filename  filename sent in the form part (e.g. {@code audio.ogg})
     * @param mimeType  MIME type of the audio (e.g. {@code audio/ogg})
     * @return the encoded multipart body as a byte array
     */
    private byte[] buildMultipartBody(String boundary, byte[] audioData, String filename, String mimeType) {
        // Usar apenas o tipo base (ex: audio/ogg) - codecs=opus pode causar problemas no header
        String contentType = "audio/ogg";
        if (mimeType != null && !mimeType.isBlank()) {
            int semicolon = mimeType.indexOf(';');
            contentType = (semicolon > 0) ? mimeType.substring(0, semicolon).trim() : mimeType.trim();
        }
        String part1 = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String part2 = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                + "whisper-1\r\n"
                + "--" + boundary + "--\r\n";

        byte[] part1Bytes = part1.getBytes(StandardCharsets.UTF_8);
        byte[] part2Bytes = part2.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[part1Bytes.length + audioData.length + part2Bytes.length];
        System.arraycopy(part1Bytes, 0, result, 0, part1Bytes.length);
        System.arraycopy(audioData, 0, result, part1Bytes.length, audioData.length);
        System.arraycopy(part2Bytes, 0, result, part1Bytes.length + audioData.length, part2Bytes.length);

        return result;
    }
}
