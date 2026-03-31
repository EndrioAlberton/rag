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
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.port.in.AskQuestionPort;
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.RequestLogPort;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import java.time.Instant;
import java.util.concurrent.Flow.Publisher;

/**
 * Use case for asking a question and getting a response using RAG
 * (Retrieval-Augmented Generation). Pure Java — instantiated by the Composition Root.
 */
public class AskQuestionUseCase implements AskQuestionPort {

    /** Repository used to search embedding chunks relevant to the prompt. */
    private final EmbeddingRepository embeddingRepository;
    /** Port that streams the language-model response token by token. */
    private final AIPort aiPort;
    /** Port responsible for persisting the request/response audit log. */
    private final RequestLogPort requestLogPort;
    /** Vert.x instance used to dispatch audit-log writes back to the event loop. */
    private final Vertx vertx;

    /** Fallback context injected into the AI request when no RAG chunks are found. */
    private static final String DEFAULT_CONTEXT = "";

    /**
     * Creates an AskQuestionUseCase with all required collaborators.
     *
     * @param embeddingRepository repository for vector-similarity search
     * @param aiPort              language-model response generator port
     * @param requestLogPort      audit-log persistence port
     * @param vertx               Vert.x instance for event-loop dispatch
     */
    public AskQuestionUseCase(
            EmbeddingRepository embeddingRepository,
            AIPort aiPort,
            RequestLogPort requestLogPort,
            Vertx vertx) {
        this.embeddingRepository = embeddingRepository;
        this.aiPort = aiPort;
        this.requestLogPort = requestLogPort;
        this.vertx = vertx;
    }

    @Override
    public Publisher<String> execute(String session, String prompt) {
        return execute(session, prompt, null, null, null);
    }

    @Override
    public Publisher<String> execute(String session, String prompt,
            String phoneNumber) {
        return execute(session, prompt, phoneNumber, null, null);
    }

    @Override
    public Publisher<String> execute(String session, String prompt,
            String phoneNumber,
            String userName, String email) {
        Instant messageTimestamp = Instant.now();
        RagQuery query = new RagQuery(prompt, 1, 0.7);
        long ragStart = System.currentTimeMillis();

        Multi<String> multi = embeddingRepository.searchChunks(query)
                .flatMap(ragResponse -> {
                    long ragLatencyMs = System.currentTimeMillis() - ragStart;
                    String ragResult = ragResponse.getContexts().isEmpty()
                            ? DEFAULT_CONTEXT
                            : ragResponse.getFirstContext();

                    AIRequest aiRequest = new AIRequest(session, prompt, ragResult);
                    long llmStart = System.currentTimeMillis();
                    StringBuilder accumulator = new StringBuilder();
                    return aiPort.generateResponse(aiRequest)
                            .group().intoLists().of(20)
                            .onItem().transform(list -> String.join("", list))
                            .onItem().invoke(chunk -> accumulator.append(chunk))
                            .onCompletion().call(() -> {
                                final String fullResponse = accumulator.toString();
                                final long llmLatencyMs =
                                        System.currentTimeMillis() - llmStart;
                                // LLM stream ends on worker; jump to EventLoop
                                // before Hibernate Reactive session.
                                return Uni.createFrom().<Void>emitter(em ->
                                        vertx.getDelegate().getOrCreateContext()
                                                .runOnContext(ignored ->
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
                                                                null)
                                                                .subscribe().with(
                                                                        r -> em.complete(
                                                                                (Void) null),
                                                                        em::fail)));
                            });
                });
        return multi.convert().toPublisher();
    }
}
