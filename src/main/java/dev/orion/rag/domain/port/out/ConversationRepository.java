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
 * Driven port (out) — repository contract for {@link Conversation} persistence.
 */
public interface ConversationRepository {

    /**
     * Finds a conversation by its unique identifier without loading its messages.
     *
     * @param id conversation identifier
     * @return a Uni emitting the conversation, or {@code null} if not found
     */
    Uni<Conversation> findById(String id);

    /**
     * Finds a conversation by its identifier and eagerly loads its associated messages.
     *
     * @param id conversation identifier
     * @return a Uni emitting the fully populated conversation, or {@code null} if not found
     */
    Uni<Conversation> findByIdWithMessages(String id);

    /**
     * Finds all conversations matching the given list of identifiers.
     *
     * @param ids list of conversation identifiers to look up
     * @return a Uni emitting the matching conversations (may be an empty list)
     */
    Uni<List<Conversation>> findByIds(List<String> ids);

    /**
     * Finds all conversations associated with the given user (as participant or owner).
     *
     * @param userId user identifier
     * @return a Uni emitting the list of conversations (may be empty)
     */
    Uni<List<Conversation>> findByUserId(String userId);

    /**
     * Finds all conversations owned by the given user.
     *
     * @param userId user identifier
     * @return a Uni emitting the list of owned conversations (may be empty)
     */
    Uni<List<Conversation>> findOwnedByUserId(String userId);

    /**
     * Checks whether the given user has access to the specified conversation.
     *
     * @param userId         user identifier
     * @param conversationId conversation identifier
     * @return a Uni emitting {@code true} if access is granted, {@code false} otherwise
     */
    Uni<Boolean> userHasAccess(String userId, String conversationId);

    /**
     * Persists a new or updated conversation.
     *
     * @param conversation the conversation to persist
     * @return a Uni emitting the persisted conversation (with any generated fields populated)
     */
    Uni<Conversation> persist(Conversation conversation);

    /**
     * Flushes any pending changes to the underlying persistence store.
     *
     * @return a Uni that completes when the flush is done
     */
    Uni<Void> flush();

    /**
     * Deletes the conversation with the given identifier.
     *
     * @param id conversation identifier
     * @return a Uni emitting {@code true} if deleted, {@code false} if not found
     */
    Uni<Boolean> deleteById(String id);
}
