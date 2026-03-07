/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.rpmhub.domain.model.RequestLog;
import dev.rpmhub.domain.port.RequestLogService;
import dev.rpmhub.infrastructure.repository.RequestLogRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Implementation of RequestLogService for persisting and exporting request logs.
 */
@ApplicationScoped
public class RequestLogServiceImpl implements RequestLogService {

    private final RequestLogRepository requestLogRepository;

    @Inject
    public RequestLogServiceImpl(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    @Override
    @WithTransaction
    public Uni<Void> log(String phoneNumber, String userId, String userMessage,
            Instant messageTimestamp, String ragResult, long ragLatencyMs,
            String llmResponse, long llmLatencyMs, String conversationId) {
        RequestLog log = new RequestLog();
        log.setPhoneNumber(phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber : null);
        log.setUserId(userId);
        log.setConversationId(conversationId != null && !conversationId.isBlank() ? conversationId : null);
        log.setUserMessage(userMessage);
        log.setMessageTimestamp(LocalDateTime.ofInstant(messageTimestamp, ZoneOffset.UTC));
        log.setRagResult(ragResult);
        log.setRagLatencyMs(ragLatencyMs);
        log.setLlmResponse(llmResponse);
        log.setLlmLatencyMs(llmLatencyMs);
        log.setCreatedAt(LocalDateTime.now());
        return requestLogRepository.persist(log).replaceWithVoid();
    }

    @Override
    public Uni<String> exportToCsv() {
        return requestLogRepository.findAllOrderedByTimestamp()
                .map(logs -> toCsv(logs));
    }

    private static String escapeCsvField(String value) {
        if (value == null) return "";
        // Escape double quotes by doubling them, wrap in quotes if contains comma, newline or quote
        String s = value.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String toCsv(List<RequestLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("phone_number,user_id,conversation_id,user_message,message_timestamp,rag_result,rag_latency_ms,llm_response,llm_latency_ms\n");
        for (RequestLog log : logs) {
            sb.append(escapeCsvField(log.getPhoneNumber())).append(",");
            sb.append(escapeCsvField(log.getUserId())).append(",");
            sb.append(escapeCsvField(log.getConversationId())).append(",");
            sb.append(escapeCsvField(log.getUserMessage())).append(",");
            sb.append(escapeCsvField(log.getMessageTimestamp() != null ? log.getMessageTimestamp().toString() : "")).append(",");
            sb.append(escapeCsvField(log.getRagResult())).append(",");
            sb.append(log.getRagLatencyMs() != null ? log.getRagLatencyMs() : "").append(",");
            sb.append(escapeCsvField(log.getLlmResponse())).append(",");
            sb.append(log.getLlmLatencyMs() != null ? log.getLlmLatencyMs() : "").append("\n");
        }
        return sb.toString();
    }
}
