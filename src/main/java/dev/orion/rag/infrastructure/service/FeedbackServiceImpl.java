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
    public CompletionStage<Void> submit(String userId, String conversationId, String userMessage, String value, String comment) {
        RequestLogFeedbackEntity.FeedbackValue v =
                RequestLogFeedbackEntity.FeedbackValue.valueOf(value.trim().toUpperCase());
        boolean report = (v == RequestLogFeedbackEntity.FeedbackValue.REPORT);
        if (report && (comment == null || comment.isBlank())) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("comentário obrigatório para reportar"));
        }
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
                            // REPORT is neither like nor dislike: keep liked null so dashboard counts ignore it
                            fb.setLiked(report ? null : liked);
                            fb.setComment(report ? comment.trim() : null);
                            fb.setUserMessage(log.getUserMessage());
                            fb.setLlmResponse(log.getLlmResponse());
                            return feedbackRepo.persist(fb).replaceWithVoid();
                        })
        ).subscribeAsCompletionStage();
    }
}

