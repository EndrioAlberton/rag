/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.port.ConversationRepository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Implementation of ConversationRepository using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class ConversationRepositoryImpl implements PanacheRepositoryBase<Conversation, String>, ConversationRepository {
    
    @Override
    public Uni<Conversation> findById(String id) {
        return find("id", id).firstResult();
    }
    
    @Override
    public Uni<Conversation> findByIdWithMessages(String id) {
        // Usar fetch join para carregar a coleção messages junto com a conversa
        return find("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = ?1", id).firstResult();
    }
    
    @Override
    public Uni<List<Conversation>> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        // Usar uma query simples com IN para buscar múltiplas conversações
        return find("id IN (?1)", ids).list();
    }
    
    @Override
    public Uni<List<Conversation>> findOwnedByUserId(String userId) {
        // Usar API Panache com owner.id - não faz JOIN quando acessa apenas o ID
        return list("owner.id", userId);
    }
    
    @Override
    public Uni<List<Conversation>> findByUserId(String userId) {
        // Buscar conversações onde o usuário é owner
        return findOwnedByUserId(userId);
    }
    
    @Override
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        // Usar API Panache para verificar acesso
        return count("id = ?1 and owner.id = ?2", conversationId, userId)
            .map(count -> count > 0);
    }
    
    @Override
    public Uni<Conversation> persist(Conversation conversation) {
        return PanacheRepositoryBase.super.persist(conversation);
    }
    
    @Override
    public Uni<Void> flush() {
        return PanacheRepositoryBase.super.flush();
    }
    
    @Override
    public Uni<Boolean> deleteById(String id) {
        // Usar delete() com query em vez de super.deleteById() para evitar
        // IllegalStateException quando o repositório implementa interface adicional
        // (bug do Panache bytecode enhancement - issue quarkus#29296)
        return delete("id", id).map(count -> count > 0);
    }
}

