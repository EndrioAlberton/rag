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

package dev.orion.rag.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_log_feedback")
@Getter
@Setter
public class RequestLogFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "request_log_id", nullable = false)
    private String requestLogId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "conversation_id")
    private String conversationId;

    /**
     * Boolean snapshot of feedback value:
     * true = like, false = dislike.
     * Nullable for legacy rows; new rows should always set it.
     */
    @Column(name = "liked")
    private Boolean liked;

    /** Snapshot of question at feedback time. */
    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    /** Snapshot of AI answer at feedback time. */
    @Column(name = "llm_response", columnDefinition = "TEXT")
    private String llmResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "value", nullable = false)
    private FeedbackValue value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum FeedbackValue {
        LIKE,
        DISLIKE
    }
}

