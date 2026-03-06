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
import dev.rpmhub.domain.model.ChatMessage;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.port.AIService;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.domain.port.MemoryService;
import dev.rpmhub.domain.port.RequestLogService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Use case for interacting with a chatbot using RAG
 * (Retrieval-Augmented Generation).
 */
@ApplicationScoped
public class ChatbotUseCase {

    /**
     * Repository for managing embeddings.
     */
    private final EmbeddingRepository embeddingRepository;

    /**
     * Service for AI interactions.
     */
    private final AIService aiService;

    /**
     * Service for managing conversation memory.
     */
    private final MemoryService memoryService;

    /**
     * Service for request logging.
     */
    private final RequestLogService requestLogService;

    /**
     * Default context to use when no context is found.
     */
    @ConfigProperty(name = "rag.context", defaultValue = "")
    private static final String DEFAULT_CONTEXT = "";

    @Inject
    public ChatbotUseCase(EmbeddingRepository embeddingRepository, AIService aiService, MemoryService memoryService,
            RequestLogService requestLogService) {
        this.embeddingRepository = embeddingRepository;
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.requestLogService = requestLogService;
    }

    /**
     * Executes the use case to interact with the chatbot (backward compatibility).
     *
     * @param session the session ID
     * @param prompt  the user prompt
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> execute(String session, String prompt) {
        return executeWithPhone(session, prompt, null);
    }

    /**
     * Executes the use case to interact with the chatbot (e.g. from WhatsApp).
     *
     * @param session     the session ID
     * @param prompt      the user prompt
     * @param phoneNumber phone number (WhatsApp) or null for REST
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> executeWithPhone(String session, String prompt, String phoneNumber) {
        Log.info("Executing ChatbotUseCase for session: " + session + " with prompt: " + prompt);
        Instant messageTimestamp = Instant.now();
        ChatMessage userMessage = new ChatMessage(session, prompt, ChatMessage.MessageType.USER);

        return memoryService.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info("Saved user message for session: " + session))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);
                    long ragStart = System.currentTimeMillis();

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                long ragLatencyMs = System.currentTimeMillis() - ragStart;
                                String ragResult = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : ragResponse.getFirstContext();
                                Log.info("Context: " + ragResult);

                                return memoryService.getHistory(session)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(session, prompt, ragResult, history);
                                            long llmStart = System.currentTimeMillis();
                                            return aiService.generateContextualResponse(aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list -> String.join("", list))
                                                    .onItem().call(response -> {
                                                        long llmLatencyMs = System.currentTimeMillis() - llmStart;
                                                        ChatMessage assistantMessage = new ChatMessage(session,
                                                                response,
                                                                ChatMessage.MessageType.ASSISTANT);
                                                        return memoryService.saveMessage(assistantMessage)
                                                                .chain(() -> requestLogService.log(
                                                                        phoneNumber, session, prompt,
                                                                        messageTimestamp, ragResult, ragLatencyMs,
                                                                        response, llmLatencyMs));
                                                    });
                                        });
                            });
                });
    }
    
    /**
     * Executes the use case to interact with the chatbot with user and conversation.
     *
     * @param userId         the user ID
     * @param conversationId the conversation ID
     * @param prompt         the user prompt
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> execute(String userId, String conversationId, String prompt) {
        return executeWithPhone(userId, conversationId, prompt, null);
    }

    /**
     * Executes the use case to interact with the chatbot with user and conversation.
     *
     * @param userId         the user ID
     * @param conversationId the conversation ID
     * @param prompt         the user prompt
     * @param phoneNumber    phone number (WhatsApp) or null for REST
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> executeWithPhone(String userId, String conversationId, String prompt, String phoneNumber) {
        Log.info("Executing ChatbotUseCase for user: " + userId + ", conversation: " + conversationId + " with prompt: " + prompt);
        Instant messageTimestamp = Instant.now();
        ChatMessage userMessage = new ChatMessage(userId, conversationId, prompt, ChatMessage.MessageType.USER);

        return memoryService.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info("Saved user message for conversation: " + conversationId))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);
                    long ragStart = System.currentTimeMillis();

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                long ragLatencyMs = System.currentTimeMillis() - ragStart;
                                String ragResult = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : ragResponse.getFirstContext();
                                Log.info("Context: " + ragResult);

                                return memoryService.getHistory(userId, conversationId)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(conversationId, prompt, ragResult, history);
                                            long llmStart = System.currentTimeMillis();
                                            return aiService.generateContextualResponse(aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list -> String.join("", list))
                                                    .onItem().call(response -> {
                                                        long llmLatencyMs = System.currentTimeMillis() - llmStart;
                                                        ChatMessage assistantMessage = new ChatMessage();
                                                        assistantMessage.setConversationId(conversationId);
                                                        assistantMessage.setSessionId(conversationId);
                                                        assistantMessage.setContent(response);
                                                        assistantMessage.setType(ChatMessage.MessageType.ASSISTANT);
                                                        assistantMessage.setUserId(null);
                                                        return memoryService.saveMessage(assistantMessage)
                                                                .chain(() -> requestLogService.log(
                                                                        phoneNumber, userId, prompt,
                                                                        messageTimestamp, ragResult, ragLatencyMs,
                                                                        response, llmLatencyMs));
                                                    });
                                        });
                            });
                });
    }
}
