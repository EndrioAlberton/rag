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

package dev.orion.rag.domain.model;

/**
 * Domain representation of an AI request — pure Java, no framework annotations.
 */
public class AIRequest {

    /** Session identifier that groups related messages in the same conversation. */
    private final String session;
    /** User's input prompt or question to be answered by the AI. */
    private final String prompt;
    /** RAG-retrieved context passages injected to augment the prompt. */
    private final String context;
    /** Serialised conversation history from previous exchanges in the session. */
    private final String history;

    /**
     * Creates an AIRequest without a prior conversation history.
     *
     * @param session session identifier
     * @param prompt  user's question or instruction
     * @param context RAG context passages retrieved for this prompt
     */
    public AIRequest(String session, String prompt, String context) {
        this.session = session;
        this.prompt = prompt;
        this.context = context;
        this.history = "";
    }

    /**
     * Creates an AIRequest including a serialised conversation history.
     *
     * @param session session identifier
     * @param prompt  user's question or instruction
     * @param context RAG context passages retrieved for this prompt
     * @param history previous conversation turns formatted as a plain string
     */
    public AIRequest(String session, String prompt, String context, String
        history) {
        this.session = session;
        this.prompt = prompt;
        this.context = context;
        this.history = history;
    }

    public String getSession() {
        return session;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getContext() {
        return context;
    }

    public String getHistory() {
        return history;
    }
}
