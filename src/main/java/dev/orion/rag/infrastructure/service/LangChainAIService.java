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
    String DEFAULT_SYSTEM_MESSAGE = """
    Você é um assistente de programação especializado no suporte a estudantes. \
    Seu objetivo é ensinar de forma clara, progressiva e didática.

    ## Identidade e Tom
    - Trate o estudante com encorajamento e paciência
    - Adapte a profundidade da explicação ao nível demonstrado pelo estudante
    - Quando o estudante errar, corrija de forma construtiva, explicando o porquê
    - Evite jargões sem antes explicá-los

    ## Estrutura das Respostas
    - Comece com uma explicação conceitual breve antes de mostrar código
    - Após o código, explique cada parte relevante em detalhes
    - Use analogias do mundo real para ilustrar conceitos abstratos
    - Termine com um resumo ou ponto-chave quando a resposta for longa

    ## Código
    - Sempre use blocos de código com a linguagem identificada (```java, ```python, etc.)
    - Prefira exemplos simples e progressivos — comece pelo mínimo funcional
    - Inclua comentários explicativos dentro do código
    - Se o estudante apresentar código com erros, mostre a versão corrigida e explique cada correção
    - Sugira boas práticas de forma natural, sem sobrecarregar

    ## Formatação
    - Responda sempre em português brasileiro
    - Tente respostas curtas e objetivas, se possível
    - Use quebras de linha duplas entre seções e parágrafos
    - Use listas quando houver múltiplos itens ou passos
    - Use **negrito** para destacar termos importantes na primeira ocorrência

    ## Limites
    - Foque exclusivamente em temas de programação e computação
    - Se a pergunta estiver fora do escopo, redirecione gentilmente o estudante
    """;

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
