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
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementação reactiva de {@link ConversationService} usando Hibernate
 * Reactive Panache.
 *
 * <p>Resolve o identificador do utilizador tanto pelo ID interno como pelo
 * hash do Orion Users (enviado pelo frontend no JWT), garantindo
 * compatibilidade com ambos os formatos.
 */
@ApplicationScoped
public class ConversationServiceImpl implements ConversationServicePort {

    /**
     * Repositório de conversas — persiste e consulta {@link Conversation}.
     */
    private final ConversationRepository conversationRepository;

    /**
     * Repositório de utilizadores — usado para resolver o userId ou hash
     * antes de operar sobre conversas.
     */
    private final UserRepository userRepository;

    /**
     * Constrói a implementação injetando os repositórios necessários.
     *
     * @param conversationRepository repositório de conversas
     * @param userRepository         repositório de utilizadores
     */
    @Inject
    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Cria uma nova conversa para o utilizador identificado por {@code userId}.
     *
     * <p>O {@code userId} pode ser o hash do Orion Users (enviado pelo
     * frontend) ou o UUID interno; ambos são tentados em sequência.
     * Lança {@link IllegalArgumentException} se o utilizador não for
     * encontrado.
     *
     * @param userId identificador ou hash do utilizador proprietário
     * @param title  título da conversa
     * @return {@link Uni} que emite a conversa persistida
     */
    @Override
    @WithTransaction
    public Uni<Conversation> createConversation(String userId, String title) {
        return userRepository.findByOrionUserHash(userId)
                .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
                .onItem().ifNull().failWith(() -> new IllegalArgumentException(
                        "Usuário não encontrado. Certifique-se de que o usuário "
                                + "foi sincronizado do JWT token."))
                .onItem().transformToUni(user -> {
                    Conversation conversation = new Conversation();
                    conversation.setTitle(title);
                    conversation.setOwner(user);
                    return conversationRepository.persist(conversation)
                            .onItem().transformToUni(persisted ->
                                    conversationRepository.flush()
                                            .replaceWith(persisted));
                });
    }

    /**
     * Devolve a conversa com o identificador fornecido.
     *
     * <p>Lança {@link IllegalArgumentException} se a conversa não existir.
     *
     * @param conversationId UUID da conversa
     * @return {@link Uni} que emite a {@link Conversation} encontrada
     */
    @Override
    @WithSession
    public Uni<Conversation> getConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException(
                        "Conversa não encontrada"));
    }

    /**
     * Lista todas as conversas pertencentes ao utilizador.
     *
     * <p>Se o utilizador não for encontrado, devolve uma lista vazia em vez
     * de propagar um erro, pois o utilizador pode ainda estar a ser criado
     * assincronamente. Falhas de persistência são registadas e suprimidas,
     * devolvendo também lista vazia.
     *
     * @param userId identificador ou hash do utilizador
     * @return {@link Uni} que emite a lista de conversas (pode ser vazia)
     */
    @Override
    @WithSession
    public Uni<List<Conversation>> getUserConversations(String userId) {
        return userRepository.findByOrionUserHash(userId)
                .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
                .onItem().transformToUni(user -> {
                    if (user == null) {
                        Log.debug("User not found (ID or hash: " + userId
                                + "), returning empty list.");
                        return Uni.createFrom().item(List.<Conversation>of());
                    }
                    Log.debug("User found, fetching conversations for ID: "
                            + user.getId());
                    return conversationRepository.findOwnedByUserId(user.getId());
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("Error fetching conversations for user " + userId
                            + ": " + e.getMessage(), e);
                    return List.<Conversation>of();
                });
    }

    /**
     * Verifica se o utilizador tem acesso de leitura/escrita à conversa.
     *
     * <p>Devolve {@code false} se o utilizador não for encontrado ou se
     * ocorrer qualquer erro durante a verificação, sem propagar exceção.
     *
     * @param userId         identificador ou hash do utilizador
     * @param conversationId UUID da conversa a verificar
     * @return {@link Uni} que emite {@code true} se o acesso for permitido,
     *         {@code false} caso contrário
     */
    @Override
    @WithSession
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        return userRepository.findByOrionUserHash(userId)
                .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
                .onItem().transformToUni(user -> {
                    if (user == null) {
                        return Uni.createFrom().item(false);
                    }
                    return conversationRepository.userHasAccess(user.getId(),
                            conversationId);
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("Error verifying user " + userId
                            + " access to conversation " + conversationId, e);
                    return false;
                });
    }

    /**
     * Elimina a conversa indicada, desde que o utilizador seja o seu dono.
     *
     * <p>Lança {@link SecurityException} se o utilizador não for o
     * proprietário, e {@link IllegalArgumentException} se a conversa não
     * existir.
     *
     * @param conversationId UUID da conversa a eliminar
     * @param userId         identificador ou hash do utilizador que solicita
     *                       a eliminação
     * @return {@link Uni} que completa em {@code Void} após a eliminação
     */
    @Override
    @WithTransaction
    public Uni<Void> deleteConversation(String conversationId, String userId) {
        return conversationRepository.userHasAccess(userId, conversationId)
                .onItem().transformToUni(hasAccess -> {
                    if (!hasAccess) {
                        return Uni.createFrom().failure(new SecurityException(
                                "Apenas o dono pode deletar a conversa"));
                    }
                    return conversationRepository.deleteById(conversationId)
                            .onItem().transformToUni(deleted -> {
                                if (!deleted) {
                                    return Uni.createFrom().failure(
                                            new IllegalArgumentException(
                                                    "Conversa não encontrada"));
                                }
                                return conversationRepository.flush();
                            });
                });
    }
}
