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

package dev.orion.rag.domain.port.in;

import java.util.concurrent.Flow.Publisher;

/**
 * Driving port (in) for asking a question using RAG (Retrieval-Augmented Generation).
 */
public interface AskQuestionPort {

    /**
     * Asks a question and streams the AI response for the given session.
     *
     * @param session session identifier
     * @param prompt  user's question
     * @return token-by-token response stream
     */
    Publisher<String> execute(String session, String prompt);

    /**
     * Asks a question and streams the AI response, tagging the log with a phone number.
     *
     * @param session     session identifier
     * @param prompt      user's question
     * @param phoneNumber caller's phone number for audit logging (may be null)
     * @return token-by-token response stream
     */
    Publisher<String> execute(String session, String prompt, String
        phoneNumber);

    /**
     * Asks a question and streams the AI response with full caller metadata for audit logging.
     *
     * @param session     session identifier
     * @param prompt      user's question
     * @param phoneNumber caller's phone number (may be null)
     * @param userName    caller's display name (may be null)
     * @param email       caller's e-mail address (may be null)
     * @return token-by-token response stream
     */
    Publisher<String> execute(String session, String prompt, String phoneNumber,
            String userName, String email);
}
