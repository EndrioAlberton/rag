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

import dev.orion.rag.domain.model.RequestLog;
import dev.orion.rag.domain.port.out.RequestLogPort;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import dev.orion.rag.infrastructure.persistence.RequestLogEntity;
import dev.orion.rag.infrastructure.repository.RequestLogPanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link RequestLogPort} for persisting and exporting request logs.
 * Uses {@link Mutiny.SessionFactory} for programmatic transaction management and converts
 * to {@link CompletionStage} at the domain boundary.
 */
@ApplicationScoped
public class RequestLogServiceImpl implements RequestLogPort {

    /** Panache repository for request log entity persistence. */
    private final RequestLogPanacheRepository requestLogPanacheRepository;
    /** Hibernate Reactive session factory for programmatic transaction management. */
    private final Mutiny.SessionFactory sessionFactory;

    /**
     * Creates a RequestLogServiceImpl with all required dependencies.
     *
     * @param requestLogPanacheRepository Panache repository for log persistence
     * @param sessionFactory              Hibernate Reactive session factory
     */
    @Inject
    public RequestLogServiceImpl(
            RequestLogPanacheRepository requestLogPanacheRepository,
            Mutiny.SessionFactory sessionFactory) {
        this.requestLogPanacheRepository = requestLogPanacheRepository;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletionStage<Void> log(String phoneNumber, String userId, String userName,
            String email, String userMessage,
            Instant messageTimestamp, String ragResult, long ragLatencyMs,
            String llmResponse, long llmLatencyMs, String conversationId) {
        return sessionFactory.withTransaction(session -> {
            RequestLogEntity entity = new RequestLogEntity();
            entity.setPhoneNumber(phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber : null);
            entity.setUserId(userId);
            entity.setUserName(userName != null && !userName.isBlank() ? userName : null);
            entity.setEmail(email != null && !email.isBlank() ? email : null);
            entity.setConversationId(conversationId != null && !conversationId.isBlank() ? conversationId : null);
            entity.setUserMessage(userMessage);
            entity.setMessageTimestamp(LocalDateTime.ofInstant(messageTimestamp, ZoneOffset.UTC));
            entity.setRagResult(ragResult);
            entity.setRagLatencyMs(ragLatencyMs);
            entity.setLlmResponse(llmResponse);
            entity.setLlmLatencyMs(llmLatencyMs);
            entity.setCreatedAt(LocalDateTime.now());
            return requestLogPanacheRepository.persist(entity).replaceWithVoid();
        }).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<String> exportToCsv() {
        return sessionFactory.withSession(session ->
                requestLogPanacheRepository.findAllOrderedByTimestamp()
                        .map(entities -> entities.stream()
                                .map(EntityMapper::toDomain)
                                .toList())
                        .map(RequestLogServiceImpl::toCsv)
        ).subscribeAsCompletionStage();
    }

    /**
     * Escapes a CSV field value by wrapping it in double quotes if necessary.
     *
     * @param value the raw field value
     * @return CSV-safe string
     */
    private static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        String s = value.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    /**
     * Converts a list of {@link RequestLog} domain objects to a CSV string.
     *
     * @param logs the list of log entries
     * @return CSV-formatted string including header row
     */
    private static String toCsv(List<RequestLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("phone_number,user_id,user_name,email,conversation_id,"
                + "user_message,message_timestamp,rag_result,rag_latency_ms,"
                + "llm_response,llm_latency_ms\n");
        for (RequestLog log : logs) {
            sb.append(escapeCsvField(log.getPhoneNumber())).append(",");
            sb.append(escapeCsvField(log.getUserId())).append(",");
            sb.append(escapeCsvField(log.getUserName())).append(",");
            sb.append(escapeCsvField(log.getEmail())).append(",");
            sb.append(escapeCsvField(log.getConversationId())).append(",");
            sb.append(escapeCsvField(log.getUserMessage())).append(",");
            sb.append(escapeCsvField(log.getMessageTimestamp() != null
                    ? log.getMessageTimestamp().toString() : "")).append(",");
            sb.append(escapeCsvField(log.getRagResult())).append(",");
            sb.append(log.getRagLatencyMs() != null ? log.getRagLatencyMs() : "").append(",");
            sb.append(escapeCsvField(log.getLlmResponse())).append(",");
            sb.append(log.getLlmLatencyMs() != null ? log.getLlmLatencyMs() : "").append("\n");
        }
        return sb.toString();
    }
}
