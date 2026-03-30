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
import dev.orion.rag.domain.port.out.AIService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Adapter for the AIService interface, using a personalized AI service
 * implementation.
 */
@ApplicationScoped
public class AIServiceAdapter implements AIService {

    /** LangChain4j AI service registered via {@code @RegisterAiService}. */
    private final LangChainAIService ai;

    /**
     * Creates an AIServiceAdapter wrapping the given LangChain4j service.
     *
     * @param langChainService the LangChain4j service to delegate to
     */
    @Inject
    public AIServiceAdapter(LangChainAIService langChainService) {
        this.ai = langChainService;
    }

    @Override
    public Multi<String> generateResponse(AIRequest request) {
        return ai.generateResponse(request.getPrompt());
    }

    @Override
    public Multi<String> generateContextualResponse(AIRequest request) {
        return ai.generateContextualResponse(
                request.getHistory(),
                request.getContext(),
                request.getPrompt());
    }
}
