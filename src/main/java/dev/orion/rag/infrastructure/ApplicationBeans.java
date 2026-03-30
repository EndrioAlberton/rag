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

package dev.orion.rag.infrastructure;

import dev.orion.rag.domain.port.in.AskQuestionPort;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.domain.port.in.IngestDocumentsPort;
import dev.orion.rag.domain.port.in.IngestFromUrlsPort;
import dev.orion.rag.domain.port.out.AIService;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryService;
import dev.orion.rag.domain.port.out.RequestLogService;
import dev.orion.rag.domain.port.out.WebScraperPort;
import dev.orion.rag.domain.usecase.AskQuestionUseCase;
import dev.orion.rag.domain.usecase.ChatbotUseCase;
import dev.orion.rag.domain.usecase.IngestDocumentsUseCase;
import dev.orion.rag.domain.usecase.IngestFromUrlsUseCase;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Composition Root — the only point where the domain is wired to infrastructure.
 * Instantiates use cases with {@code new} (pure Java) and exposes them as CDI beans
 * via {@code @Produces}, following the Hexagonal Architecture pattern.
 */
@ApplicationScoped
public class ApplicationBeans {

    /** Infrastructure implementation of the embedding repository (PGVector). */
    @Inject
    EmbeddingRepository embeddingRepository;

    /** Infrastructure implementation of the AI service (LangChain4j + OpenAI). */
    @Inject
    AIService aiService;

    /** Infrastructure implementation of the conversation memory service (PostgreSQL + Redis). */
    @Inject
    MemoryService memoryService;

    /** Infrastructure implementation of the request-log persistence service. */
    @Inject
    RequestLogService requestLogService;

    /** Infrastructure implementation of the web-scraper port (HTTP + HTML→Markdown). */
    @Inject
    WebScraperPort webScraperPort;

    /** Vert.x instance injected to provide the event-loop context to use cases. */
    @Inject
    Vertx vertx;

    /**
     * Produces the {@link AskQuestionPort} CDI bean by instantiating the pure-Java use case
     * with its infrastructure collaborators.
     *
     * @return a new {@link AskQuestionUseCase} wired with all required dependencies
     */
    @Produces
    @ApplicationScoped
    public AskQuestionPort askQuestionPort() {
        return new AskQuestionUseCase(embeddingRepository, aiService,
            requestLogService, vertx);
    }

    /**
     * Produces the {@link ChatbotPort} CDI bean by instantiating the pure-Java use case
     * with its infrastructure collaborators.
     *
     * @return a new {@link ChatbotUseCase} wired with all required dependencies
     */
    @Produces
    @ApplicationScoped
    public ChatbotPort chatbotPort() {
        return new ChatbotUseCase(embeddingRepository, aiService, memoryService,
            requestLogService);
    }

    /**
     * Produces the {@link IngestDocumentsPort} CDI bean by instantiating the use case.
     *
     * @return a new {@link IngestDocumentsUseCase}
     */
    @Produces
    @ApplicationScoped
    public IngestDocumentsPort ingestDocumentsPort() {
        return new IngestDocumentsUseCase(embeddingRepository);
    }

    /**
     * Produces the {@link IngestFromUrlsPort} CDI bean by instantiating the use case.
     *
     * @return a new {@link IngestFromUrlsUseCase}
     */
    @Produces
    @ApplicationScoped
    public IngestFromUrlsPort ingestFromUrlsPort() {
        return new IngestFromUrlsUseCase(embeddingRepository, webScraperPort);
    }
}
