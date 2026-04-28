package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.port.out.FeedbackPort;
import dev.orion.rag.infrastructure.persistence.RequestLogFeedbackEntity;
import dev.orion.rag.infrastructure.repository.RequestLogFeedbackPanacheRepository;
import dev.orion.rag.infrastructure.repository.RequestLogPanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class FeedbackServiceImpl implements FeedbackPort {

    private final RequestLogPanacheRepository requestLogRepo;
    private final RequestLogFeedbackPanacheRepository feedbackRepo;
    private final Mutiny.SessionFactory sessionFactory;

    @Inject
    public FeedbackServiceImpl(
            RequestLogPanacheRepository requestLogRepo,
            RequestLogFeedbackPanacheRepository feedbackRepo,
            Mutiny.SessionFactory sessionFactory) {
        this.requestLogRepo = requestLogRepo;
        this.feedbackRepo = feedbackRepo;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletionStage<Void> submit(String userId, String conversationId, String userMessage, String value) {
        RequestLogFeedbackEntity.FeedbackValue v =
                RequestLogFeedbackEntity.FeedbackValue.valueOf(value.trim().toUpperCase());
        boolean liked = (v == RequestLogFeedbackEntity.FeedbackValue.LIKE);

        return sessionFactory.withTransaction(session ->
                requestLogRepo.findLatestByConversationAndPrompt(userId, conversationId, userMessage)
                        .onItem().ifNull().failWith(new IllegalArgumentException("request_log não encontrado"))
                        .onItem().transformToUni(log -> {
                            RequestLogFeedbackEntity fb = new RequestLogFeedbackEntity();
                            fb.setRequestLogId(log.getId());
                            fb.setUserId(userId);
                            fb.setConversationId(conversationId);
                            fb.setValue(v);
                            fb.setLiked(liked);
                            fb.setUserMessage(log.getUserMessage());
                            fb.setLlmResponse(log.getLlmResponse());
                            return feedbackRepo.persist(fb).replaceWithVoid();
                        })
        ).subscribeAsCompletionStage();
    }
}

