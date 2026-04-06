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

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

/**
 * Personalized AI service interface for handling AI interactions.
 */
@RegisterAiService
public interface LangChainAIService {

    /**
     * Default system prompt injected into every conversation.
     * Configures the assistant to answer in Portuguese, explain code in detail,
     * and format responses with proper line breaks for readability.
     */
    String DEFAULT_SYSTEM_MESSAGE = "Você é um assistente de programação "
            + "para estudantes você deve detalhar os exemplos de código e explicar "
            + "conceitos. Responda em português. "
            + "IMPORTANTE: Sempre formate suas respostas com quebras de linha "
            + "adequadas. "
            + "Use quebras de linha duplas (\\n\\n) para separar parágrafos e "
            + "seções. "
            + "Use quebras de linha simples (\\n) dentro de parágrafos quando "
            + "necessário. "
            + "Formate listas, títulos e seções com espaçamento adequado para "
            + "melhor legibilidade.";

    /**
     * Generates a streaming response for the given prompt without conversation context.
     *
     * @param prompt user's question or instruction
     * @return a Multi emitting response tokens as they are produced by the model
     */
    @SystemMessage(DEFAULT_SYSTEM_MESSAGE)
    @UserMessage("{prompt}")
    Multi<String> generateResponse(String prompt);

    /**
     * Generates a streaming response that takes prior conversation history and RAG context into account.
     *
     * @param history serialised conversation history (role: content pairs)
     * @param context RAG-retrieved context passages for the current prompt
     * @param prompt  user's question or instruction
     * @return a Multi emitting response tokens as they are produced by the model
     */
    @SystemMessage(DEFAULT_SYSTEM_MESSAGE)
    @UserMessage("Histórico: {history}, Contexto: {context}, pergunta: {prompt}")
    Multi<String> generateContextualResponse(String history, String context,
            String prompt);
}
