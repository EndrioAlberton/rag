/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.rag.domain.usecase;

import dev.orion.rag.domain.model.AIRequest;
import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;

import java.time.Instant;
import java.util.concurrent.Flow.Publisher;

/**
 * Use case for interacting with a chatbot using RAG
 * (Retrieval-Augmented Generation). Pure Java — instantiated by the Composition Root.
 */
public class ChatbotUseCase implements ChatbotPort {

    /** Repository used to search embedding chunks relevant to the prompt. */
    private final EmbeddingRepository embeddingRepository;
    /** Port that streams the language-model response token by token. */
    private final AIPort aiPort;
    /** Port that loads and persists the per-session conversation memory. */
    private final MemoryPort memoryPort;
    /** Port responsible for persisting the request/response audit log. */
    private final RequestLogPort requestLogPort;

    /** Fallback context injected into the AI request when no RAG chunks are found. */
    private static final String DEFAULT_CONTEXT = "";

    /**
     * Creates a ChatbotUseCase with all required collaborators.
     *
     * @param embeddingRepository repository for vector-similarity search
     * @param aiPort              language-model response generator port
     * @param memoryPort          conversation memory loader and persister port
     * @param requestLogPort      audit-log persistence port
     */
    public ChatbotUseCase(
            EmbeddingRepository embeddingRepository,
            AIPort aiPort,
            MemoryPort memoryPort,
            RequestLogPort requestLogPort) {
        this.embeddingRepository = embeddingRepository;
        this.aiPort = aiPort;
        this.memoryPort = memoryPort;
        this.requestLogPort = requestLogPort;
    }

    /**
     * Converts a Mutiny {@link Multi} stream to a {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param multi Mutiny stream to convert
     * @return equivalent {@code Flow.Publisher}
     */
    private static Publisher<String> toFlow(Multi<String> multi) {
        return multi.convert().toPublisher();
    }

    @Override
    public Publisher<String> execute(String session, String prompt) {
        return executeWithPhone(session, prompt, null, null, null);
    }

    @Override
    public Publisher<String> execute(String userId, String conversationId,
            String prompt) {
        return executeWithPhone(userId, conversationId, prompt, null, null, null);
    }

    @Override
    public Publisher<String> executeWithPhone(String session, String prompt,
            String phoneNumber) {
        return executeWithPhone(session, prompt, phoneNumber, null, null);
    }

    @Override
    public Publisher<String> executeWithPhone(String session, String prompt,
            String phoneNumber,
            String userName, String email) {
        return toFlow(chatMultiForSession(session, prompt, phoneNumber, userName,
                email));
    }

    @Override
    public Publisher<String> executeWithPhone(String userId,
            String conversationId, String prompt,
            String phoneNumber, String userName, String email) {
        return toFlow(chatMultiForUserConversation(userId, conversationId, prompt,
                phoneNumber, userName, email));
    }

    /**
     * Builds the reactive Multi pipeline for an anonymous session-based chatbot interaction.
     *
     * @param session     session identifier
     * @param prompt      user's question
     * @param phoneNumber caller's phone number for audit logging (may be null)
     * @param userName    display name for audit logging (may be null)
     * @param email       e-mail for audit logging (may be null)
     * @return token-by-token response stream
     */
    private Multi<String> chatMultiForSession(String session, String prompt,
            String phoneNumber,
            String userName, String email) {
        Log.info("Executing ChatbotUseCase for session: " + session
                + " with prompt: " + prompt);
        Instant messageTimestamp = Instant.now();
        ChatMessage userMessage = new ChatMessage(session, prompt,
                ChatMessage.MessageType.USER);

        return memoryPort.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info("Saved user message for session: "
                        + session))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);
                    long ragStart = System.currentTimeMillis();

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                long ragLatencyMs =
                                        System.currentTimeMillis() - ragStart;
                                String ragResult =
                                        ragResponse.getContexts().isEmpty()
                                                ? DEFAULT_CONTEXT
                                                : ragResponse.getFirstContext();

                                return memoryPort.getHistory(session)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(
                                                    session, prompt, ragResult,
                                                    history);
                                            long llmStart =
                                                    System.currentTimeMillis();
                                            StringBuilder accumulator =
                                                    new StringBuilder();
                                            return aiPort
                                                    .generateContextualResponse(
                                                            aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list ->
                                                            String.join("", list))
                                                    .onItem().invoke(chunk ->
                                                            accumulator.append(
                                                                    chunk))
                                                    .onCompletion().call(() -> {
                                                        long llmLatencyMs =
                                                                System.currentTimeMillis()
                                                                        - llmStart;
                                                        String fullResponse =
                                                                accumulator.toString();
                                                        ChatMessage assistantMessage =
                                                                new ChatMessage(
                                                                        session,
                                                                        fullResponse,
                                                                        ChatMessage.MessageType.ASSISTANT);
                                                        return memoryPort
                                                                .saveMessage(
                                                                        assistantMessage)
                                                                .chain(() ->
                                                                        requestLogPort.log(
                                                                                phoneNumber,
                                                                                session,
                                                                                userName,
                                                                                email,
                                                                                prompt,
                                                                                messageTimestamp,
                                                                                ragResult,
                                                                                ragLatencyMs,
                                                                                fullResponse,
                                                                                llmLatencyMs,
                                                                                null));
                                                    });
                                        });
                            });
                });
    }

    /**
     * Builds the reactive Multi pipeline for an authenticated user/conversation chatbot interaction.
     *
     * @param userId         authenticated user identifier
     * @param conversationId identifier of the persisted conversation
     * @param prompt         user's question
     * @param phoneNumber    caller's phone number for audit logging (may be null)
     * @param userName       display name for audit logging (may be null)
     * @param email          e-mail for audit logging (may be null)
     * @return token-by-token response stream
     */
    private Multi<String> chatMultiForUserConversation(String userId,
            String conversationId, String prompt,
            String phoneNumber, String userName, String email) {
        Log.info("Executing ChatbotUseCase for user: " + userId
                + ", conversation: " + conversationId + " with prompt: "
                + prompt);
        Instant messageTimestamp = Instant.now();
        ChatMessage userMessage = new ChatMessage(userId, conversationId, prompt,
                ChatMessage.MessageType.USER);

        return memoryPort.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info(
                        "Saved user message for conversation: "
                                + conversationId))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);
                    long ragStart = System.currentTimeMillis();

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                long ragLatencyMs =
                                        System.currentTimeMillis() - ragStart;
                                String ragResult =
                                        ragResponse.getContexts().isEmpty()
                                                ? DEFAULT_CONTEXT
                                                : ragResponse.getFirstContext();

                                return memoryPort.getHistory(userId,
                                                conversationId)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(
                                                    conversationId, prompt,
                                                    ragResult, history);
                                            long llmStart =
                                                    System.currentTimeMillis();
                                            StringBuilder accumulator =
                                                    new StringBuilder();
                                            return aiPort
                                                    .generateContextualResponse(
                                                            aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list ->
                                                            String.join("", list))
                                                    .onItem().invoke(chunk ->
                                                            accumulator.append(
                                                                    chunk))
                                                    .onCompletion().call(() -> {
                                                        long llmLatencyMs =
                                                                System.currentTimeMillis()
                                                                        - llmStart;
                                                        String fullResponse =
                                                                accumulator.toString();
                                                        ChatMessage assistantMessage =
                                                                new ChatMessage();
                                                        assistantMessage.setConversationId(
                                                                conversationId);
                                                        assistantMessage.setSessionId(
                                                                conversationId);
                                                        assistantMessage.setContent(
                                                                fullResponse);
                                                        assistantMessage.setType(
                                                                ChatMessage.MessageType.ASSISTANT);
                                                        assistantMessage.setUserId(
                                                                null);
                                                        return memoryPort
                                                                .saveMessage(
                                                                        assistantMessage)
                                                                .chain(() ->
                                                                        requestLogPort.log(
                                                                                phoneNumber,
                                                                                userId,
                                                                                userName,
                                                                                email,
                                                                                prompt,
                                                                                messageTimestamp,
                                                                                ragResult,
                                                                                ragLatencyMs,
                                                                                fullResponse,
                                                                                llmLatencyMs,
                                                                                conversationId));
                                                    });
                                        });
                            });
                });
    }
}
