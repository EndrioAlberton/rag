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

package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.AIRequest;
import io.smallrye.mutiny.Multi;

/**
 * Driven port (out) for language-model interactions.
 * The domain defines this contract; the infrastructure adapter implements it.
 */
public interface AIService {

    /**
     * Generates a response based on the provided AI request.
     *
     * @param request the AI request
     * @return a Multi emitting the generated response token by token
     */
    Multi<String> generateResponse(AIRequest request);

    /**
     * Generates a contextual response that takes the conversation history into account.
     *
     * @param request the AI request containing the context and history
     * @return a Multi emitting the generated contextual response token by token
     */
    Multi<String> generateContextualResponse(AIRequest request);
}
