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
import dev.orion.rag.domain.model.TriagemResult;
import dev.orion.rag.domain.model.TriagemResult.Decisao;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import dev.orion.rag.domain.port.out.TriagemPort;
import dev.orion.rag.domain.support.AccumulatingOnCompletePublisher;
import dev.orion.rag.domain.support.AppendOnCompletePublisher;
import dev.orion.rag.domain.support.RagSourceFormatter;
import dev.orion.rag.domain.support.DeferredPublisher;

import java.time.Instant;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

/**
 * Use case for interacting with a chatbot using RAG
 * (Retrieval-Augmented Generation). Pure Java — instantiated by the Composition Root.
 */
public class ChatbotUseCase implements ChatbotPort {

    private static final Logger LOG = Logger.getLogger(ChatbotUseCase.class.getName());
    /** Fallback context string used when no RAG result is found. */
    private static final String DEFAULT_CONTEXT = "";
    private static final String HUMAN_SUPPORT_CONTACT = """

---
Suporte humano (IFRS POA)
E-mail: comunicacao@poa.ifrs.edu.br
Telefone: (51) 3930-6002
""";

    /** Repository used to search embedding chunks relevant to the prompt. */
    private final EmbeddingRepository embeddingRepository;
    /** Port that streams the language-model response token by token. */
    private final AIPort aiPort;
    /** Port that loads and persists the per-session conversation memory. */
    private final MemoryPort memoryPort;
    /** Port responsible for persisting the request/response audit log. */
    private final RequestLogPort requestLogPort;
    /** Port for classifying message urgency and triagem decision. */
    private final TriagemPort triagemPort;

    /**
     * Creates a ChatbotUseCase with all required collaborators.
     *
     * @param embeddingRepository repository for vector-similarity search
     * @param aiPort              language-model response generator port
     * @param memoryPort          conversation memory loader and persister port
     * @param requestLogPort      audit-log persistence port
     * @param triagemPort         triagem classification port
     */
    public ChatbotUseCase(
            EmbeddingRepository embeddingRepository,
            AIPort aiPort,
            MemoryPort memoryPort,
            RequestLogPort requestLogPort,
            TriagemPort triagemPort) {
        this.embeddingRepository = embeddingRepository;
        this.aiPort = aiPort;
        this.memoryPort = memoryPort;
        this.requestLogPort = requestLogPort;
        this.triagemPort = triagemPort;
    }

    @Override
    public Flow.Publisher<String> execute(String session, String prompt) {
        return executeWithPhone(session, prompt, null, null, null);
    }

    @Override
    public Flow.Publisher<String> execute(String userId, String conversationId, String prompt) {
        return executeWithPhone(userId, conversationId, prompt, null, null, null);
    }

    @Override
    public Flow.Publisher<String> executeWithPhone(String session, String prompt, String phoneNumber) {
        return executeWithPhone(session, prompt, phoneNumber, null, null);
    }

    @Override
    public Flow.Publisher<String> executeWithPhone(String session, String prompt,
            String phoneNumber, String userName, String email) {
        LOG.info("ChatbotUseCase (session): " + session);
        Instant messageTimestamp = Instant.now();
        ChatMessage userMsg = new ChatMessage(session, prompt, ChatMessage.MessageType.USER);

        return new DeferredPublisher<>(() ->
            memoryPort.saveMessage(userMsg)
                .thenCompose(v -> memoryPort.getHistory(session))
                .thenCompose(history -> triagemPort.classify(prompt, history)
                    .thenCompose(triage -> {
                        // PEDIR_INFO: ask user for more info, skip RAG
                        if (triage.getDecisao() == Decisao.PEDIR_INFO) {
                            String campos = triage.getCamposFaltantes() != null
                                    ? " Para continuar, informe: " + triage.getCamposFaltantes() + "."
                                    : "";
                            String pedirMsg = "Para responder melhor, preciso de mais detalhes." + campos;
                            ChatMessage clarificationMsg = new ChatMessage(
                                    session, pedirMsg, ChatMessage.MessageType.ASSISTANT);
                            return memoryPort.saveMessage(clarificationMsg)
                                    .thenApply(v2 -> publishSingle(pedirMsg));
                        }
                        RagQuery query = new RagQuery(prompt, 6, 0.55);
                        long ragStart = System.currentTimeMillis();
                        String urgency = triage.getUrgencia().name();
                        return embeddingRepository.searchChunks(query)
                            .thenApply(ragResponse -> {
                                long ragLatencyMs = System.currentTimeMillis() - ragStart;
                                String ragResult = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : String.join("\n\n---\n\n", ragResponse.getContexts());
                                long llmStart = System.currentTimeMillis();
                                boolean handoffRequired = ragResult == null || ragResult.isBlank();
                                String handoffReason = handoffRequired ? "no_context" : null;

                                Flow.Publisher<String> withAppendix;
                                if (handoffRequired) {
                                    String msg = "Não encontrei informação suficiente na base para responder com segurança."
                                            + HUMAN_SUPPORT_CONTACT;
                                    withAppendix = publishSingle(msg);
                                } else {
                                    AIRequest aiRequest = new AIRequest(session, prompt, ragResult, history);
                                    Flow.Publisher<String> stream =
                                            aiPort.generateContextualResponse(aiRequest);
                                    String sourcesAppendix = RagSourceFormatter.sourcesAppendix(ragResult);
                                    withAppendix = new AppendOnCompletePublisher(stream, sourcesAppendix);
                                }
                                return (Flow.Publisher<String>) new AccumulatingOnCompletePublisher(
                                        withAppendix,
                                        fullResponse -> {
                                            long llmLatencyMs =
                                                    System.currentTimeMillis() - llmStart;
                                            ChatMessage assistantMsg = new ChatMessage(
                                                    session, fullResponse,
                                                    ChatMessage.MessageType.ASSISTANT);
                                            return memoryPort.saveMessage(assistantMsg)
                                                    .thenCompose(v2 -> requestLogPort.log(
                                                            phoneNumber, session, userName,
                                                            email, prompt, messageTimestamp,
                                                            ragResult, ragResponse.getScore(), ragLatencyMs,
                                                            handoffRequired, handoffReason,
                                                            fullResponse, llmLatencyMs, null, urgency));
                                        });
                            });
                    })
                )
        );
    }

    @Override
    public Flow.Publisher<String> executeWithPhone(String userId, String conversationId,
            String prompt, String phoneNumber, String userName, String email) {
        LOG.info("ChatbotUseCase (user: " + userId + ", conversation: " + conversationId + ")");
        Instant messageTimestamp = Instant.now();
        ChatMessage userMsg = new ChatMessage(userId, conversationId, prompt,
                ChatMessage.MessageType.USER);

        return new DeferredPublisher<>(() ->
            memoryPort.saveMessage(userMsg)
                .thenCompose(v -> memoryPort.getHistory(userId, conversationId))
                .thenCompose(history -> triagemPort.classify(prompt, history)
                    .thenCompose(triage -> {
                        // PEDIR_INFO: ask user for more info, skip RAG
                        if (triage.getDecisao() == Decisao.PEDIR_INFO) {
                            String campos = triage.getCamposFaltantes() != null
                                    ? " Para continuar, informe: " + triage.getCamposFaltantes() + "."
                                    : "";
                            String pedirMsg = "Para responder melhor, preciso de mais detalhes." + campos;
                            ChatMessage clarificationMsg = new ChatMessage();
                            clarificationMsg.setConversationId(conversationId);
                            clarificationMsg.setSessionId(conversationId);
                            clarificationMsg.setContent(pedirMsg);
                            clarificationMsg.setType(ChatMessage.MessageType.ASSISTANT);
                            clarificationMsg.setUserId(null);
                            return memoryPort.saveMessage(clarificationMsg)
                                    .thenApply(v2 -> publishSingle(pedirMsg));
                        }
                        RagQuery query = new RagQuery(prompt, 6, 0.55);
                        long ragStart = System.currentTimeMillis();
                        String urgency = triage.getUrgencia().name();
                        return embeddingRepository.searchChunks(query)
                            .thenApply(ragResponse -> {
                                long ragLatencyMs = System.currentTimeMillis() - ragStart;
                                String ragResult = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : String.join("\n\n---\n\n", ragResponse.getContexts());
                                long llmStart = System.currentTimeMillis();
                                boolean handoffRequired = ragResult == null || ragResult.isBlank();
                                String handoffReason = handoffRequired ? "no_context" : null;

                                Flow.Publisher<String> withAppendix;
                                if (handoffRequired) {
                                    String msg = "Não encontrei informação suficiente na base para responder com segurança."
                                            + HUMAN_SUPPORT_CONTACT;
                                    withAppendix = publishSingle(msg);
                                } else {
                                    AIRequest aiRequest = new AIRequest(
                                            conversationId, prompt, ragResult, history);
                                    Flow.Publisher<String> stream =
                                            aiPort.generateContextualResponse(aiRequest);
                                    String sourcesAppendix = RagSourceFormatter.sourcesAppendix(ragResult);
                                    withAppendix = new AppendOnCompletePublisher(stream, sourcesAppendix);
                                }
                                return (Flow.Publisher<String>) new AccumulatingOnCompletePublisher(
                                        withAppendix,
                                        fullResponse -> {
                                            long llmLatencyMs =
                                                    System.currentTimeMillis() - llmStart;
                                            ChatMessage assistantMsg = new ChatMessage();
                                            assistantMsg.setConversationId(conversationId);
                                            assistantMsg.setSessionId(conversationId);
                                            assistantMsg.setContent(fullResponse);
                                            assistantMsg.setType(
                                                    ChatMessage.MessageType.ASSISTANT);
                                            assistantMsg.setUserId(null);
                                            return memoryPort.saveMessage(assistantMsg)
                                                    .thenCompose(v2 -> requestLogPort.log(
                                                            phoneNumber, userId, userName,
                                                            email, prompt, messageTimestamp,
                                                            ragResult, ragResponse.getScore(), ragLatencyMs,
                                                            handoffRequired, handoffReason,
                                                            fullResponse, llmLatencyMs,
                                                            conversationId, urgency));
                                        });
                            });
                    })
                )
        );
    }

    /** Helper to create a single-item publisher for fixed messages. */
    private static Flow.Publisher<String> publishSingle(String message) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean done = false;
            @Override public void request(long n) {
                if (done) return;
                done = true;
                subscriber.onNext(message);
                subscriber.onComplete();
            }
            @Override public void cancel() { done = true; }
        });
    }
}

