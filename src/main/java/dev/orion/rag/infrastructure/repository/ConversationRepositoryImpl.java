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
import dev.orion.rag.infrastructure.persistence.ConversationEntity;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of ConversationRepository using Hibernate Reactive Panache.
 * Converts Mutiny {@code Uni<T>} to {@link CompletionStage} at the boundary.
 */
@ApplicationScoped
public class ConversationRepositoryImpl implements ConversationRepository {

    /**
     * Hibernate Reactive does not lazy-load associations; owner must be fetched with the conversation.
     */
    private static final String HQL_BY_ID_WITH_OWNER =
            "SELECT DISTINCT c FROM ConversationEntity c JOIN FETCH c.owner WHERE c.id = ?1";

    /** Loads conversation, owner and messages for mapping to domain. */
    private static final String HQL_BY_ID_WITH_OWNER_AND_MESSAGES =
            "SELECT DISTINCT c FROM ConversationEntity c JOIN FETCH c.owner "
                    + "LEFT JOIN FETCH c.messages WHERE c.id = ?1";

    /** Panache repository used for all JPA queries and persistence operations on conversations. */
    @Inject
    ConversationPanacheRepository panache;

    @Override
    public CompletionStage<Conversation> findById(String id) {
        return panache.find(HQL_BY_ID_WITH_OWNER, id)
                .<ConversationEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Conversation> findByIdWithMessages(String id) {
        return panache.find(HQL_BY_ID_WITH_OWNER_AND_MESSAGES, id)
                .<ConversationEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<Conversation>> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Uni.createFrom().<List<Conversation>>item(List.of())
                    .subscribeAsCompletionStage();
        }
        return panache.find(
                        "SELECT DISTINCT c FROM ConversationEntity c JOIN FETCH c.owner WHERE c.id IN (?1)",
                        ids)
                .<ConversationEntity>list()
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList())
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<Conversation>> findOwnedByUserId(String userId) {
        return panache.find(
                        "SELECT DISTINCT c FROM ConversationEntity c JOIN FETCH c.owner "
                                + "WHERE c.owner.id = ?1 ORDER BY c.lastActivity DESC",
                        userId)
                .<ConversationEntity>list()
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList())
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<Conversation>> findByUserId(String userId) {
        return findOwnedByUserId(userId);
    }

    @Override
    public CompletionStage<Boolean> userHasAccess(String userId, String conversationId) {
        return panache.count("id = ?1 and owner.id = ?2", conversationId, userId)
                .map(count -> count > 0)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Conversation> persist(Conversation conversation) {
        ConversationEntity entity = EntityMapper.toEntity(conversation);
        return panache.persist(entity)
                .call(() -> panache.flush())
                .chain(e -> panache.find(HQL_BY_ID_WITH_OWNER, e.getId())
                        .<ConversationEntity>firstResult()
                        .map(EntityMapper::toDomain))
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> flush() {
        return panache.flush().subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> deleteById(String id) {
        return panache.delete("id", id)
                .map(count -> count > 0)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> updateTitle(String id, String title) {
        return panache.find("id", id).<ConversationEntity>firstResult()
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"))
                .chain(entity -> {
                    entity.setTitle(title);
                    return panache.flush();
                })
                .subscribeAsCompletionStage();
    }
}
