package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.DashboardMetrics;
import dev.orion.rag.domain.model.InteractionSummary;
import dev.orion.rag.domain.port.out.DashboardPort;
import dev.orion.rag.infrastructure.repository.ConversationPanacheRepository;
import dev.orion.rag.infrastructure.repository.RequestLogFeedbackPanacheRepository;
import dev.orion.rag.infrastructure.repository.RequestLogPanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class DashboardServiceImpl implements DashboardPort {

    private final RequestLogPanacheRepository requestLogRepo;
    private final RequestLogFeedbackPanacheRepository feedbackRepo;
    private final ConversationPanacheRepository conversationRepo;
    private final Mutiny.SessionFactory sessionFactory;

    @Inject
    public DashboardServiceImpl(
            RequestLogPanacheRepository requestLogRepo,
            RequestLogFeedbackPanacheRepository feedbackRepo,
            ConversationPanacheRepository conversationRepo,
            Mutiny.SessionFactory sessionFactory) {
        this.requestLogRepo = requestLogRepo;
        this.feedbackRepo = feedbackRepo;
        this.conversationRepo = conversationRepo;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletionStage<DashboardMetrics> metrics() {
        return sessionFactory.withSession(session -> {
            var totalRequests = requestLogRepo.count();
            var totalConversations = conversationRepo.count();
            var handoff = requestLogRepo.count("handoffRequired", true);
            var likes = feedbackRepo.countLikes();
            var dislikes = feedbackRepo.countDislikes();
            var urgLow = requestLogRepo.countByUrgency("BAIXA");
            var urgMed = requestLogRepo.countByUrgency("MEDIA");
            var urgHigh = requestLogRepo.countByUrgency("ALTA");

            return io.smallrye.mutiny.Uni.combine().all()
                    .unis(totalRequests, totalConversations, handoff, likes, dislikes, urgLow, urgMed, urgHigh)
                    .asTuple()
                    .map(t -> {
                        DashboardMetrics m = new DashboardMetrics();
                        m.setTotalRequests(t.getItem1());
                        m.setTotalConversations(t.getItem2());
                        m.setHandoffRequired(t.getItem3());
                        m.setLikes(t.getItem4());
                        m.setDislikes(t.getItem5());
                        m.setUrgencyLow(t.getItem6());
                        m.setUrgencyMedium(t.getItem7());
                        m.setUrgencyHigh(t.getItem8());
                        return m;
                    });
        }).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<InteractionSummary>> interactionsByUrgency(String urgency) {
        return sessionFactory.withSession(session ->
            requestLogRepo.findByUrgencyOrderedByDate(urgency.toUpperCase())
                .map(entities -> entities.stream()
                    .map(e -> new InteractionSummary(
                            e.getId(),
                            e.getUserMessage(),
                            e.getLlmResponse(),
                            e.getUrgency(),
                            e.getCreatedAt() != null ? e.getCreatedAt().toString() : null))
                    .collect(Collectors.toList()))
        ).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<InteractionSummary>> interactionsByFeedback(boolean liked) {
        return sessionFactory.withSession(session ->
            feedbackRepo.findByLikedOrderedByDate(liked)
                .map(entities -> entities.stream()
                    .map(e -> new InteractionSummary(
                            e.getId(),
                            e.getUserMessage(),
                            e.getLlmResponse(),
                            null,
                            e.getCreatedAt() != null ? e.getCreatedAt().toString() : null))
                    .collect(Collectors.toList()))
        ).subscribeAsCompletionStage();
    }
}

