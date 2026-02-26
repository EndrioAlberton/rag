/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.port.ConversationRepository;
import dev.rpmhub.domain.port.ConversationService;
import dev.rpmhub.domain.port.UserRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of ConversationService.
 */
@ApplicationScoped
public class ConversationServiceImpl implements ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    
    @Inject
    public ConversationServiceImpl(ConversationRepository conversationRepository, 
                                   UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    @WithTransaction
    public Uni<Conversation> createConversation(String userId, String title) {
        // O frontend envia o hash do Orion Users como userId
        // Primeiro tentar buscar pelo hash do Orion Users (mais comum)
        return userRepository.findByOrionUserHash(userId)
            .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado. Certifique-se de que o usuário foi sincronizado do JWT token."))
            .onItem().transformToUni(user -> {
                Conversation conversation = new Conversation();
                conversation.setTitle(title);
                conversation.setOwner(user);
                return conversationRepository.persist(conversation)
                    .onItem().transformToUni(persisted -> conversationRepository.flush().replaceWith(persisted));
            });
    }
    
    @Override
    @WithSession
    public Uni<Conversation> getConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"));
    }
    
    @Override
    @WithSession
    public Uni<List<Conversation>> getUserConversations(String userId) {
        // O frontend envia o hash do Orion Users como userId
        // Primeiro tentar buscar pelo hash do Orion Users (mais comum)
        return userRepository.findByOrionUserHash(userId)
            .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
            .onItem().transformToUni(user -> {
                if (user == null) {
                    // Usuário não encontrado, pode estar sendo criado assincronamente
                    // Retornar lista vazia em vez de erro
                    Log.debug("User not found (ID or hash: " + userId + "), returning empty list.");
                    return Uni.createFrom().item(List.<Conversation>of());
                }
                // Usuário encontrado, buscar conversações usando o ID real
                Log.debug("User found, fetching conversations for ID: " + user.getId());
                return conversationRepository.findOwnedByUserId(user.getId());
            })
            .onFailure().recoverWithItem(e -> {
                Log.error("Error fetching conversations for user " + userId + ": " + e.getMessage(), e);
                return List.<Conversation>of();
            });
    }
    
    @Override
    @WithSession
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        // O userId pode ser tanto orionUserHash quanto id do usuário
        // Primeiro tentar buscar pelo hash do Orion Users (mais comum)
        return userRepository.findByOrionUserHash(userId)
            .onItem().ifNull().switchTo(() -> userRepository.findById(userId))
            .onItem().transformToUni(user -> {
                if (user == null) {
                    // Usuário não encontrado, retornar false
                    return Uni.createFrom().item(false);
                }
                // Usar o ID real do usuário para verificar acesso
                return conversationRepository.userHasAccess(user.getId(), conversationId);
            })
            .onFailure().recoverWithItem(e -> {
                Log.error("Error verifying user " + userId + " access to conversation " + conversationId, e);
                return false;
            });
    }
    
    @Override
    @WithTransaction
    public Uni<Void> deleteConversation(String conversationId, String userId) {
        // Verificar se o usuário tem acesso antes de deletar
        return conversationRepository.userHasAccess(userId, conversationId)
            .onItem().transformToUni(hasAccess -> {
                if (!hasAccess) {
                    return Uni.createFrom().failure(new SecurityException("Apenas o dono pode deletar a conversa"));
                }
                return conversationRepository.deleteById(conversationId)
                    .onItem().transformToUni(deleted -> {
                        if (!deleted) {
                            return Uni.createFrom().failure(new IllegalArgumentException("Conversa não encontrada"));
                        }
                        return conversationRepository.flush();
                    });
            });
    }
}

