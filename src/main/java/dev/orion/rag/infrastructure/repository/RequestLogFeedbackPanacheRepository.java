package dev.orion.rag.infrastructure.repository;

import dev.orion.rag.infrastructure.persistence.RequestLogFeedbackEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RequestLogFeedbackPanacheRepository implements PanacheRepositoryBase<RequestLogFeedbackEntity, String> {

    public Uni<Long> countLikes() {
        return count("liked", true);
    }

    public Uni<Long> countDislikes() {
        return count("liked", false);
    }

    public Uni<List<RequestLogFeedbackEntity>> findByLikedOrderedByDate(boolean liked) {
        return find("liked = ?1 ORDER BY createdAt DESC", liked).list();
    }
}

