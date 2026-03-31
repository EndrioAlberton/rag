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

package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.AIRequest;
import dev.orion.rag.domain.port.out.AIPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.Flow;

/**
 * Implementation of {@link AIPort} that delegates to the LangChain4j AI service.
 * Converts Mutiny {@code Multi<String>} to {@link Flow.Publisher} at the boundary.
 */
@ApplicationScoped
public class AIPortImpl implements AIPort {

    /** LangChain4j AI service registered via {@code @RegisterAiService}. */
    private final LangChainAIService ai;

    @Inject
    public AIPortImpl(LangChainAIService langChainService) {
        this.ai = langChainService;
    }

    @Override
    public Flow.Publisher<String> generateResponse(AIRequest request) {
        return ai.generateResponse(request.getPrompt()).convert().toPublisher();
    }

    @Override
    public Flow.Publisher<String> generateContextualResponse(AIRequest request) {
        return ai.generateContextualResponse(
                request.getHistory(),
                request.getContext(),
                request.getPrompt()).convert().toPublisher();
    }
}
