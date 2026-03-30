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

package dev.orion.rag.infrastructure.repository;

import dev.orion.rag.domain.model.Conversation;
import dev.orion.rag.domain.port.out.ConversationRepository;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import dev.orion.rag.infrastructure.persistence.ConversationEntity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of ConversationRepository using Hibernate Reactive Panache.
 * Delegates to ConversationPanacheRepository for JPA operations and converts
 * between ConversationEntity (JPA) and Conversation (domain) via EntityMapper.
 */
@ApplicationScoped
public class ConversationRepositoryImpl implements ConversationRepository {

    /** Panache repository used for all JPA queries and persistence operations. */
    @Inject
    ConversationPanacheRepository panache;

    @Override
    public Uni<Conversation> findById(String id) {
        return panache.find("id", id).<ConversationEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<Conversation> findByIdWithMessages(String id) {
        return panache.find(
                "SELECT c FROM ConversationEntity c LEFT JOIN FETCH c.messages "
                        + "WHERE c.id = ?1",
                id)
                .<ConversationEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<List<Conversation>> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return panache.find("id IN (?1)", ids).<ConversationEntity>list()
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList());
    }

    @Override
    public Uni<List<Conversation>> findOwnedByUserId(String userId) {
        return panache.list("owner.id", userId)
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList());
    }

    @Override
    public Uni<List<Conversation>> findByUserId(String userId) {
        return findOwnedByUserId(userId);
    }

    @Override
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        return panache.count("id = ?1 and owner.id = ?2", conversationId,
            userId)
                .map(count -> count > 0);
    }

    @Override
    public Uni<Conversation> persist(Conversation conversation) {
        ConversationEntity entity = EntityMapper.toEntity(conversation);
        return panache.persist(entity)
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<Void> flush() {
        return panache.flush();
    }

    @Override
    public Uni<Boolean> deleteById(String id) {
        return panache.delete("id", id).map(count -> count > 0);
    }
}
