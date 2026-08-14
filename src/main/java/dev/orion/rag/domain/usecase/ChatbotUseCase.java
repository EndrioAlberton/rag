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
import java.util.concurrent.CompletionStage;
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
    /**
     * Fixed answer for questions about the assistant itself (scope, purpose, capabilities).
     * Returned directly by triagem's {@code SOBRE_ASSISTENTE} decision, bypassing RAG and
     * the LLM entirely: these questions have nothing to do with the knowledge base, so
     * running a similarity search for them only risks surfacing unrelated chunks that the
     * LLM would then either misuse or, more often, correctly ignore while still getting a
     * "Fontes consultadas" footer appended — a response that contradicts itself.
     */
    private static final String ABOUT_ASSISTANT_MESSAGE = """
Sou o assistente virtual do curso de Sistemas para Internet (SSI) do IFRS – Campus Porto Alegre.

Posso ajudar com:
- Grade curricular, disciplinas e pré-requisitos
- Trabalho de Conclusão de Curso (TCC) e estágio
- Matrícula, rematrícula e calendário acadêmico
- Frequência, faltas, reprovação e atividades complementares
- Regulamentos, editais e dúvidas frequentes do curso

Minhas respostas são baseadas nos documentos oficiais do IFRS (PPC, editais e FAQ). Quando não encontro a resposta na base, oriento você a procurar o suporte humano.
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
    public Flow.Publisher<String> execute(String userId, String conversationId, String prompt) {
        return execute(userId, conversationId, prompt, null, null);
    }

    @Override
    public Flow.Publisher<String> execute(String userId, String conversationId,
            String prompt, String userName, String email) {
        LOG.info("ChatbotUseCase (user: " + userId + ", conversation: " + conversationId + ")");
        Instant messageTimestamp = Instant.now();
        ChatMessage userMsg = new ChatMessage(userId, conversationId, prompt,
                ChatMessage.MessageType.USER);

        return new DeferredPublisher<>(() ->
            memoryPort.saveMessage(userMsg)
                .thenCompose(v -> memoryPort.getHistory(userId, conversationId))
                .thenCompose(history -> triagemPort.classify(prompt, history)
                    .thenCompose(triage -> {
                        // SOBRE_ASSISTENTE: question about the bot itself, not the course — skip RAG entirely
                        if (triage.getDecisao() == Decisao.SOBRE_ASSISTENTE) {
                            return saveCannedAssistantMessage(conversationId, ABOUT_ASSISTANT_MESSAGE);
                        }
                        // PEDIR_INFO: ask user for more info, skip RAG
                        if (triage.getDecisao() == Decisao.PEDIR_INFO) {
                            String campos = triage.getCamposFaltantes() != null
                                    ? " Para continuar, informe: " + triage.getCamposFaltantes() + "."
                                    : "";
                            String pedirMsg = "Para responder melhor, preciso de mais detalhes." + campos;
                            return saveCannedAssistantMessage(conversationId, pedirMsg);
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
                                                            userId, userName,
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

    /**
     * Persists a fixed assistant message — no RAG lookup, no LLM call — and returns a
     * publisher that streams it as a single item. Shared by the {@code PEDIR_INFO} and
     * {@code SOBRE_ASSISTENTE} triagem branches.
     *
     * @param conversationId conversation to persist the message under
     * @param message        fixed message content
     * @return a CompletionStage emitting a single-item publisher of the message
     */
    private CompletionStage<Flow.Publisher<String>> saveCannedAssistantMessage(
            String conversationId, String message) {
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setSessionId(conversationId);
        assistantMsg.setContent(message);
        assistantMsg.setType(ChatMessage.MessageType.ASSISTANT);
        assistantMsg.setUserId(null);
        return memoryPort.saveMessage(assistantMsg)
                .thenApply(v -> publishSingle(message));
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

