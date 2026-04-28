package dev.orion.rag.domain.port.out;

import java.util.concurrent.CompletionStage;

public interface FeedbackPort {

    CompletionStage<Void> submit(String userId, String conversationId, String userMessage, String value);
}

