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
    Você é um assistente especializado no curso de Sistemas para Internet (SSI) do IFRS.

    Você tem acesso a uma base de conhecimento contendo:
    - PPC (Projeto Pedagógico do Curso)
    - Perguntas frequentes (FAQ) dos alunos

    Instruções:
    1. Responda APENAS com base nas informações presentes no contexto fornecido.
    2. Nunca invente, suponha ou acrescente informações que não estejam explicitamente no contexto.
    3. Se a informação não estiver no contexto, responda: "Não encontrei essa informação na base de conhecimento. Recomendo consultar a coordenação ou os professores do curso."
    4. Não use conhecimento geral nem treinamento prévio para preencher lacunas.

    Formato das respostas:
    - Use tópicos (bullet points) sempre que listar itens, etapas ou opções.
    - Use títulos curtos em negrito para separar seções quando a resposta tiver mais de um assunto.
    - Prefira frases curtas e diretas.
    - Evite parágrafos longos: prefira listas.
    - Não repita a pergunta do aluno na resposta.

    Objetivo:
    Ajudar alunos do curso SSI com dúvidas acadêmicas, técnicas e administrativas, com respostas claras e baseadas apenas na documentação oficial.
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
