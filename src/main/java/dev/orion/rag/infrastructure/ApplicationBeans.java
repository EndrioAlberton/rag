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
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import dev.orion.rag.domain.port.out.WebScraperPort;
import dev.orion.rag.domain.usecase.AskQuestionUseCase;
import dev.orion.rag.domain.usecase.ChatbotUseCase;
import dev.orion.rag.domain.usecase.IngestDocumentsUseCase;
import dev.orion.rag.domain.usecase.IngestFromUrlsUseCase;
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

    /** Port for embedding-based retrieval. */
    @Inject
    EmbeddingRepository embeddingRepository;

    /** Port that streams language-model responses. */
    @Inject
    AIPort aiPort;

    /** Port that manages per-session conversation memory. */
    @Inject
    MemoryPort memoryPort;

    /** Port for persisting request/response audit logs. */
    @Inject
    RequestLogPort requestLogPort;

    /** Port for web content scraping. */
    @Inject
    WebScraperPort webScraperPort;

    @Produces
    @ApplicationScoped
    public AskQuestionPort askQuestionPort() {
        return new AskQuestionUseCase(embeddingRepository, aiPort, requestLogPort);
    }

    @Produces
    @ApplicationScoped
    public ChatbotPort chatbotPort() {
        return new ChatbotUseCase(embeddingRepository, aiPort, memoryPort, requestLogPort);
    }

    @Produces
    @ApplicationScoped
    public IngestDocumentsPort ingestDocumentsPort() {
        return new IngestDocumentsUseCase(embeddingRepository);
    }

    @Produces
    @ApplicationScoped
    public IngestFromUrlsPort ingestFromUrlsPort() {
        return new IngestFromUrlsUseCase(embeddingRepository, webScraperPort);
    }
}
