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

package dev.orion.rag.domain.port.out;

import io.smallrye.mutiny.Uni;

/**
 * Driven port (out) for persisting and exporting request logs.
 *
 * @see <a href="https://github.com/orion-services/rag/issues/10">Issue #10</a>
 */
public interface RequestLogPort {

    /**
     * Persists a request log entry.
     *
     * @param phoneNumber      phone number (WhatsApp) or null for REST/MCP
     * @param userId           unique user identifier (UUID, hash, or session)
     * @param userName         display name of the user (username for REST, profile name for WhatsApp, null for MCP)
     * @param email            email of the user (available for REST via JWT, null for WhatsApp and MCP)
     * @param userMessage      message sent by the user
     * @param messageTimestamp when the message was received
     * @param ragResult        content retrieved from RAG
     * @param ragLatencyMs     RAG retrieval time in milliseconds
     * @param llmResponse      complete LLM response (after streaming finishes)
     * @param llmLatencyMs     time to receive complete LLM response in milliseconds
     * @param conversationId   unique conversation identifier (UUID) or null for legacy session-based flows
     * @return Uni that completes when the log is persisted
     */
    Uni<Void> log(String phoneNumber, String userId, String userName, String email,
            String userMessage, java.time.Instant messageTimestamp,
            String ragResult, long ragLatencyMs,
            String llmResponse, long llmLatencyMs, String conversationId);

    /**
     * Exports all request logs to CSV format.
     *
     * @return Uni emitting the CSV content as a string
     */
    Uni<String> exportToCsv();
}
