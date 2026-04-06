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

import dev.orion.rag.domain.model.Conversation;
import dev.orion.rag.domain.port.out.ConversationRepository;
import dev.orion.rag.domain.port.out.ConversationServicePort;
import dev.orion.rag.domain.port.out.UserRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Reactive implementation of {@link ConversationServicePort}.
 * Uses {@link Mutiny.SessionFactory} for programmatic transaction/session management
 * and converts to {@link CompletionStage} at the domain boundary.
 */
@ApplicationScoped
public class ConversationServiceImpl implements ConversationServicePort {

    /** Repository for conversation entity persistence. */
    private final ConversationRepository conversationRepository;
    /** Repository for user entity lookups. */
    private final UserRepository userRepository;
    /** Hibernate Reactive session factory for programmatic transaction management. */
    private final Mutiny.SessionFactory sessionFactory;

    /**
     * Creates a ConversationServiceImpl with all required dependencies.
     *
     * @param conversationRepository repository for conversation persistence
     * @param userRepository         repository for user lookups
     * @param sessionFactory         Hibernate Reactive session factory
     */
    @Inject
    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            Mutiny.SessionFactory sessionFactory) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletionStage<Conversation> createConversation(String userId, String title) {
        return sessionFactory.withTransaction(session ->
            Uni.createFrom().completionStage(() -> userRepository.findByOrionUserHash(userId))
                .onItem().ifNull().switchTo(
                        () -> Uni.createFrom().completionStage(() -> userRepository.findById(userId)))
                .onItem().ifNull().failWith(() -> new IllegalArgumentException(
                        "Usuário não encontrado. Certifique-se de que o usuário "
                                + "foi sincronizado do JWT token."))
                .onItem().transformToUni(user -> {
                    Conversation conversation = new Conversation();
                    conversation.setTitle(title);
                    conversation.setOwner(user);
                    return Uni.createFrom()
                            .completionStage(() -> conversationRepository.persist(conversation))
                            .onItem().transformToUni(persisted ->
                                    Uni.createFrom()
                                            .completionStage(() -> conversationRepository.flush())
                                            .replaceWith(persisted));
                })
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Conversation> getConversation(String conversationId) {
        return sessionFactory.withSession(session ->
            Uni.createFrom().completionStage(() -> conversationRepository.findByIdWithMessages(conversationId))
                    .onItem().ifNull().failWith(
                            () -> new IllegalArgumentException("Conversa não encontrada"))
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<Conversation>> getUserConversations(String userId) {
        return sessionFactory.withSession(session ->
            Uni.createFrom().completionStage(() -> userRepository.findByOrionUserHash(userId))
                .onItem().ifNull().switchTo(
                        () -> Uni.createFrom().completionStage(() -> userRepository.findById(userId)))
                .onItem().transformToUni(user -> {
                    if (user == null) {
                        Log.debug("User not found (ID or hash: " + userId
                                + "), returning empty list.");
                        return Uni.createFrom().item(List.<Conversation>of());
                    }
                    Log.debug("User found, fetching conversations for ID: " + user.getId());
                    return Uni.createFrom()
                            .completionStage(() -> conversationRepository.findOwnedByUserId(user.getId()));
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("Error fetching conversations for user " + userId + ": "
                            + e.getMessage(), e);
                    return List.<Conversation>of();
                })
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> userHasAccess(String userId, String conversationId) {
        return sessionFactory.withSession(session ->
            Uni.createFrom().completionStage(() -> userRepository.findByOrionUserHash(userId))
                .onItem().ifNull().switchTo(
                        () -> Uni.createFrom().completionStage(() -> userRepository.findById(userId)))
                .onItem().transformToUni(user -> {
                    if (user == null) {
                        return Uni.createFrom().item(false);
                    }
                    return Uni.createFrom().completionStage(
                            () -> conversationRepository.userHasAccess(user.getId(), conversationId));
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("Error verifying user " + userId
                            + " access to conversation " + conversationId, e);
                    return false;
                })
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> deleteConversation(String conversationId, String userId) {
        return sessionFactory.withTransaction(session ->
            Uni.createFrom()
                .completionStage(() -> conversationRepository.userHasAccess(userId, conversationId))
                .onItem().transformToUni(hasAccess -> {
                    if (!hasAccess) {
                        return Uni.createFrom().failure(
                                new SecurityException("Apenas o dono pode deletar a conversa"));
                    }
                    return Uni.createFrom()
                            .completionStage(() -> conversationRepository.deleteById(conversationId))
                            .onItem().transformToUni(deleted -> {
                                if (!deleted) {
                                    return Uni.createFrom().failure(
                                            new IllegalArgumentException("Conversa não encontrada"));
                                }
                                return Uni.createFrom()
                                        .completionStage(() -> conversationRepository.flush());
                            });
                })
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Conversation> updateConversationTitle(String conversationId, String userId,
            String title) {
        return sessionFactory.withTransaction(session ->
            Uni.createFrom().completionStage(() -> conversationRepository.userHasAccess(userId, conversationId))
                .onItem().transformToUni(hasAccess -> {
                    if (!hasAccess) {
                        return Uni.createFrom().failure(
                                new SecurityException("Apenas o dono pode alterar a conversa"));
                    }
                    return Uni.createFrom()
                            .completionStage(() -> conversationRepository.updateTitle(conversationId, title))
                            .chain(() -> Uni.createFrom().completionStage(
                                    () -> conversationRepository.findById(conversationId)));
                })
        ).subscribeAsCompletionStage();
    }
}
