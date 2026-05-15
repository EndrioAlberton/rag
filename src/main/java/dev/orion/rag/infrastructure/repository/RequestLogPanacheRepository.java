package dev.orion.rag.infrastructure.repository;

import dev.orion.rag.infrastructure.persistence.RequestLogEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Panache repository for {@link RequestLogEntity}.
 */
@ApplicationScoped
public class RequestLogPanacheRepository implements PanacheRepositoryBase<RequestLogEntity, String> {

    public Uni<List<RequestLogEntity>> findAllOrderedByTimestamp() {
        return find("ORDER BY messageTimestamp ASC").list();
    }

    public Uni<RequestLogEntity> findLatestByConversationAndPrompt(
            String userId,
            String conversationId,
            String userMessage) {
        return find("conversationId = ?1 and userMessage = ?2 ORDER BY createdAt DESC",
                conversationId, userMessage)
                .firstResult();
    }

    public Uni<List<RequestLogEntity>> findByUrgencyOrderedByDate(String urgency) {
        return find("urgency = ?1 ORDER BY createdAt DESC", urgency).list();
    }

    public Uni<Long> countByUrgency(String urgency) {
        return count("urgency", urgency);
    }
}

