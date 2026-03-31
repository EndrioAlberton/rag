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

import dev.orion.rag.domain.model.Conversation;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Driven port (out) — service contract for managing {@link Conversation} entities.
 */
public interface ConversationServicePort {

    /**
     * Creates a new conversation owned by the specified user.
     *
     * @param userId user identifier of the owner
     * @param title  human-readable title for the conversation
     * @return a Uni emitting the newly created conversation
     */
    Uni<Conversation> createConversation(String userId, String title);

    /**
     * Retrieves a conversation by its identifier.
     *
     * @param conversationId conversation identifier
     * @return a Uni emitting the conversation, or an error if not found
     */
    Uni<Conversation> getConversation(String conversationId);

    /**
     * Lists all conversations belonging to the specified user.
     *
     * @param userId user identifier
     * @return a Uni emitting the list of conversations (may be empty)
     */
    Uni<List<Conversation>> getUserConversations(String userId);

    /**
     * Checks whether the given user has access to the specified conversation.
     *
     * @param userId         user identifier
     * @param conversationId conversation identifier
     * @return a Uni emitting {@code true} if access is granted, {@code false} otherwise
     */
    Uni<Boolean> userHasAccess(String userId, String conversationId);

    /**
     * Deletes the conversation identified by the given ID, enforcing ownership.
     *
     * @param conversationId conversation identifier
     * @param userId         identifier of the user requesting the deletion
     * @return a Uni that completes when the conversation is deleted
     */
    Uni<Void> deleteConversation(String conversationId, String userId);
}
