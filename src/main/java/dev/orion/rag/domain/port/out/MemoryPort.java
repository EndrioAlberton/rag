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

import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.ConversationMemory;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Driven port (out) for managing conversation memory using PostgreSQL + Redis hybrid approach.
 * PostgreSQL for persistence, Redis for cache/performance.
 */
public interface MemoryPort {

    /**
     * Saves a chat message to the conversation memory.
     *
     * @param message the message to save
     * @return a CompletionStage that completes when the message is saved
     */
    CompletionStage<Void> saveMessage(ChatMessage message);

    /**
     * Retrieves the conversation memory for a specific session.
     *
     * @param sessionId the session identifier (for backward compatibility)
     * @return a CompletionStage containing the conversation memory, or null if not found
     */
    CompletionStage<ConversationMemory> getConversationMemory(String sessionId);

    /**
     * Retrieves the conversation memory for a specific conversation.
     *
     * @param userId         the user identifier
     * @param conversationId the conversation identifier
     * @return a CompletionStage containing the conversation memory, or null if not found
     */
    CompletionStage<ConversationMemory> getConversationMemory(String userId, String conversationId);

    /**
     * Gets the last N messages from a conversation.
     *
     * @param sessionId the session identifier (for backward compatibility)
     * @param count     the number of messages to retrieve
     * @return a CompletionStage containing list of the last N messages
     */
    CompletionStage<List<ChatMessage>> getLastMessages(String sessionId, int count);

    /**
     * Gets the last N messages from a conversation.
     *
     * @param userId         the user identifier
     * @param conversationId the conversation identifier
     * @param count          the number of messages to retrieve
     * @return a CompletionStage containing list of the last N messages
     */
    CompletionStage<List<ChatMessage>> getLastMessages(String userId, String conversationId, int count);

    /**
     * Gets the conversation history as a formatted string.
     *
     * @param sessionId the session identifier (for backward compatibility)
     * @return a CompletionStage containing the conversation history
     */
    CompletionStage<String> getHistory(String sessionId);

    /**
     * Gets the conversation history as a formatted string.
     *
     * @param userId         the user identifier
     * @param conversationId the conversation identifier
     * @return a CompletionStage containing the conversation history
     */
    CompletionStage<String> getHistory(String userId, String conversationId);

    /**
     * Clears the conversation memory for a specific session.
     *
     * @param sessionId the session identifier (for backward compatibility)
     * @return a CompletionStage that completes when the conversation is cleared
     */
    CompletionStage<Void> clearConversation(String sessionId);

    /**
     * Clears the conversation memory for a specific conversation.
     *
     * @param userId         the user identifier
     * @param conversationId the conversation identifier
     * @return a CompletionStage that completes when the conversation is cleared
     */
    CompletionStage<Void> clearConversation(String userId, String conversationId);

    /**
     * Checks if a conversation exists for the given session.
     *
     * @param sessionId the session identifier (for backward compatibility)
     * @return a CompletionStage containing true if conversation exists, false otherwise
     */
    CompletionStage<Boolean> hasConversation(String sessionId);

    /**
     * Checks if a conversation exists for the given user and conversation.
     *
     * @param userId         the user identifier
     * @param conversationId the conversation identifier
     * @return a CompletionStage containing true if conversation exists, false otherwise
     */
    CompletionStage<Boolean> hasConversation(String userId, String conversationId);

    /**
     * Sets the maximum number of messages to keep in memory for a session.
     *
     * @param sessionId   the session identifier
     * @param maxMessages the maximum number of messages
     * @return a CompletionStage that completes when the max messages is updated
     */
    CompletionStage<Void> setMaxMessages(String sessionId, int maxMessages);

    /**
     * Gets the number of messages in a conversation.
     *
     * @param sessionId the session identifier
     * @return a CompletionStage containing the number of messages
     */
    CompletionStage<Integer> getMessageCount(String sessionId);
}
