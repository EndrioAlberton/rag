/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.usecase;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.rpmhub.domain.model.AIRequest;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.port.AIService;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.domain.port.RequestLogService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Use case for asking a question and getting a response using RAG
 * (Retrieval-Augmented Generation).
 */
@ApplicationScoped
public class AskQuestionUseCase {

    private final EmbeddingRepository embeddingRepository;
    private final AIService aiService;
    private final RequestLogService requestLogService;

    @ConfigProperty(name = "rag.context", defaultValue = "")
    private static final String DEFAULT_CONTEXT = "";

    @Inject
    public AskQuestionUseCase(EmbeddingRepository embeddingRepository,
            AIService aiService,
            RequestLogService requestLogService) {
        this.embeddingRepository = embeddingRepository;
        this.aiService = aiService;
        this.requestLogService = requestLogService;
    }

    /**
     * Executes the use case to ask a question and get a response.
     *
     * @param session the session ID
     * @param prompt  the question prompt
     * @return a Multi emitting the response
     */
    public Multi<String> execute(String session, String prompt) {
        return execute(session, prompt, null);
    }

    /**
     * Executes the use case to ask a question and get a response.
     *
     * @param session     the session ID
     * @param prompt      the question prompt
     * @param phoneNumber phone number (WhatsApp) or null for REST
     * @return a Multi emitting the response
     */
    public Multi<String> execute(String session, String prompt, String phoneNumber) {
        Instant messageTimestamp = Instant.now();
        RagQuery query = new RagQuery(prompt, 1, 0.7);
        long ragStart = System.currentTimeMillis();

        return embeddingRepository.searchChunks(query)
                .flatMap(ragResponse -> {
                    long ragLatencyMs = System.currentTimeMillis() - ragStart;
                    String ragResult = ragResponse.getContexts().isEmpty()
                            ? DEFAULT_CONTEXT
                            : ragResponse.getFirstContext();

                    AIRequest aiRequest = new AIRequest(session, prompt, ragResult);
                    long llmStart = System.currentTimeMillis();
                    StringBuilder accumulator = new StringBuilder();
                    return aiService.generateResponse(aiRequest)
                            .group().intoLists().of(20)
                            .onItem().transform(list -> String.join("", list))
                            .onItem().invoke(chunk -> accumulator.append(chunk))
                            .onCompletion().call(() -> requestLogService.log(
                                    phoneNumber, session, prompt,
                                    messageTimestamp, ragResult, ragLatencyMs,
                                    accumulator.toString(), System.currentTimeMillis() - llmStart, null));
                });
    }
}
