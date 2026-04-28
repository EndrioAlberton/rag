package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.DashboardMetrics;
import dev.orion.rag.domain.port.out.DashboardPort;
import dev.orion.rag.infrastructure.repository.ConversationPanacheRepository;
import dev.orion.rag.infrastructure.repository.RequestLogFeedbackPanacheRepository;
import dev.orion.rag.infrastructure.repository.RequestLogPanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.concurrent.CompletionStage;

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

            return io.smallrye.mutiny.Uni.combine().all().unis(totalRequests, totalConversations, handoff, likes, dislikes)
                    .asTuple()
                    .map(t -> {
                        DashboardMetrics m = new DashboardMetrics();
                        m.setTotalRequests(t.getItem1());
                        m.setTotalConversations(t.getItem2());
                        m.setHandoffRequired(t.getItem3());
                        m.setLikes(t.getItem4());
                        m.setDislikes(t.getItem5());
                        return m;
                    });
        }).subscribeAsCompletionStage();
    }
}

