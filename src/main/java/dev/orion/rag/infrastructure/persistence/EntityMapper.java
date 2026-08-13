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

import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.Conversation;
import dev.orion.rag.domain.model.RequestLog;
import dev.orion.rag.domain.model.User;

import org.hibernate.Hibernate;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Mapper between domain models (pure Java) and JPA entities (infrastructure).
 */
public final class EntityMapper {

    private EntityMapper() {
    }

    // ========== User ==========

    /**
     * Converts a {@link UserEntity} to the domain {@link User} model.
     *
     * @param entity JPA entity to convert (may be null)
     * @return the equivalent domain model, or {@code null} if the entity is null
     */
    public static User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        if (!Hibernate.isInitialized(entity)) {
            return null;
        }
        User user = new User();
        user.setId(entity.getId());
        user.setUsername(entity.getUsername());
        user.setEmail(entity.getEmail());
        user.setPasswordHash(entity.getPasswordHash());
        user.setOrionUserHash(entity.getOrionUserHash());
        user.setCreatedAt(entity.getCreatedAt());
        user.setLastLogin(entity.getLastLogin());
        return user;
    }

    /**
     * Converts a domain {@link User} to a {@link UserEntity} suitable for persistence.
     *
     * @param domain domain model to convert (may be null)
     * @return the equivalent JPA entity, or {@code null} if the domain model is null
     */
    public static UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setEmail(domain.getEmail());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setOrionUserHash(domain.getOrionUserHash());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setLastLogin(domain.getLastLogin());
        return entity;
    }

    /**
     * Copies all mutable fields from the domain {@link User} into the existing {@link UserEntity}.
     * The entity's {@code id} field is not modified.
     *
     * @param entity target JPA entity to update
     * @param domain source domain model with the new values
     */
    public static void updateEntity(UserEntity entity, User domain) {
        entity.setUsername(domain.getUsername());
        entity.setEmail(domain.getEmail());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setOrionUserHash(domain.getOrionUserHash());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setLastLogin(domain.getLastLogin());
    }

    // ========== Conversation ==========

    /**
     * Converts a {@link ConversationEntity} to the domain model.
     * The {@code messages} collection is only copied when it has been initialised in the persistence
     * context (e.g. via {@code JOIN FETCH} or {@code Mutiny.fetch}); otherwise the domain object keeps an
     * empty set — Hibernate Reactive does not support lazy loading outside an explicit fetch.
     *
     * @param entity JPA entity to convert (may be null)
     * @return the equivalent domain model, or {@code null} if the entity is null
     */
    public static Conversation toDomain(ConversationEntity entity) {
        if (entity == null) {
            return null;
        }
        Conversation conv = new Conversation();
        conv.setId(entity.getId());
        conv.setTitle(entity.getTitle());
        conv.setOwner(toDomain(entity.getOwner()));
        conv.setCreatedAt(entity.getCreatedAt());
        conv.setLastActivity(entity.getLastActivity());
        if (Hibernate.isInitialized(entity.getMessages())) {
            conv.setMessages(entity.getMessages().stream()
                    .sorted(Comparator.comparing(ChatMessageEntity::getTimestamp,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(EntityMapper::toDomain)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return conv;
    }

    /**
     * Converts a domain {@link Conversation} to a {@link ConversationEntity} suitable for persistence.
     * Note: the messages collection is not transferred — manage it separately through the repository.
     *
     * @param domain domain model to convert (may be null)
     * @return the equivalent JPA entity, or {@code null} if the domain model is null
     */
    public static ConversationEntity toEntity(Conversation domain) {
        if (domain == null) {
            return null;
        }
        ConversationEntity entity = new ConversationEntity();
        entity.setId(domain.getId());
        entity.setTitle(domain.getTitle());
        entity.setOwner(toEntity(domain.getOwner()));
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setLastActivity(domain.getLastActivity());
        return entity;
    }

    // ========== ChatMessage ==========

    /**
     * Converts a {@link ChatMessageEntity} to the domain {@link ChatMessage} model.
     *
     * @param entity JPA entity to convert (may be null)
     * @return the equivalent domain model, or {@code null} if the entity is null
     */
    public static ChatMessage toDomain(ChatMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        ChatMessage msg = new ChatMessage();
        msg.setId(entity.getId());
        msg.setSessionId(entity.getSessionId());
        msg.setUserId(entity.getUserId());
        msg.setConversationId(entity.getConversationId());
        msg.setContent(entity.getContent());
        msg.setType(ChatMessage.MessageType.valueOf(entity.getType().name()));
        msg.setTimestamp(entity.getTimestamp());
        return msg;
    }

    /**
     * Converts a domain {@link ChatMessage} to a {@link ChatMessageEntity} suitable for persistence.
     *
     * @param domain domain model to convert (may be null)
     * @return the equivalent JPA entity, or {@code null} if the domain model is null
     */
    public static ChatMessageEntity toEntity(ChatMessage domain) {
        if (domain == null) {
            return null;
        }
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(domain.getId());
        entity.setSessionId(domain.getSessionId());
        entity.setUserId(domain.getUserId());
        entity.setConversationId(domain.getConversationId());
        entity.setContent(domain.getContent());
        entity.setType(ChatMessageEntity.MessageType.valueOf(domain.getType().name()));
        entity.setTimestamp(domain.getTimestamp());
        return entity;
    }

    // ========== RequestLog ==========

    /**
     * Converts a {@link RequestLogEntity} to the domain {@link RequestLog} model.
     *
     * @param entity JPA entity to convert (may be null)
     * @return the equivalent domain model, or {@code null} if the entity is null
     */
    public static RequestLog toDomain(RequestLogEntity entity) {
        if (entity == null) {
            return null;
        }
        RequestLog log = new RequestLog();
        log.setId(entity.getId());
        log.setUserId(entity.getUserId());
        log.setUserName(entity.getUserName());
        log.setEmail(entity.getEmail());
        log.setConversationId(entity.getConversationId());
        log.setUserMessage(entity.getUserMessage());
        log.setMessageTimestamp(entity.getMessageTimestamp());
        log.setRagResult(entity.getRagResult());
        log.setRagScore(entity.getRagScore());
        log.setRagLatencyMs(entity.getRagLatencyMs());
        log.setHandoffRequired(entity.isHandoffRequired());
        log.setHandoffReason(entity.getHandoffReason());
        log.setLlmResponse(entity.getLlmResponse());
        log.setLlmLatencyMs(entity.getLlmLatencyMs());
        log.setCreatedAt(entity.getCreatedAt());
        return log;
    }

    /**
     * Converts a domain {@link RequestLog} to a {@link RequestLogEntity} suitable for persistence.
     *
     * @param domain domain model to convert (may be null)
     * @return the equivalent JPA entity, or {@code null} if the domain model is null
     */
    public static RequestLogEntity toEntity(RequestLog domain) {
        if (domain == null) {
            return null;
        }
        RequestLogEntity entity = new RequestLogEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setUserName(domain.getUserName());
        entity.setEmail(domain.getEmail());
        entity.setConversationId(domain.getConversationId());
        entity.setUserMessage(domain.getUserMessage());
        entity.setMessageTimestamp(domain.getMessageTimestamp());
        entity.setRagResult(domain.getRagResult());
        entity.setRagScore(domain.getRagScore());
        entity.setRagLatencyMs(domain.getRagLatencyMs());
        entity.setHandoffRequired(domain.isHandoffRequired());
        entity.setHandoffReason(domain.getHandoffReason());
        entity.setLlmResponse(domain.getLlmResponse());
        entity.setLlmLatencyMs(domain.getLlmLatencyMs());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
