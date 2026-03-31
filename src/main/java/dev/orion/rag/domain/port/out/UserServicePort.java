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

import dev.orion.rag.domain.model.User;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Driven port (out) — service contract for managing {@link User} entities.
 */
public interface UserServicePort {

    /**
     * Creates a new user with the given username and e-mail.
     *
     * @param username desired login username
     * @param email    user's e-mail address
     * @return a Uni emitting the newly created user
     */
    Uni<User> createUser(String username, String email);

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param userId user identifier
     * @return a Uni emitting the user, or an error if not found
     */
    Uni<User> getUserById(String userId);

    /**
     * Retrieves a user by their login username.
     *
     * @param username login username
     * @return a Uni emitting the user, or an error if not found
     */
    Uni<User> getUserByUsername(String username);

    /**
     * Retrieves a user by their e-mail address.
     *
     * @param email e-mail address
     * @return a Uni emitting the user, or an error if not found
     */
    Uni<User> getUserByEmail(String email);

    /**
     * Persists updated fields for the given user.
     *
     * @param user the user object with the updated fields
     * @return a Uni that completes when the update is flushed
     */
    Uni<Void> updateUser(User user);

    /**
     * Deletes the user identified by the given ID.
     *
     * @param userId user identifier
     * @return a Uni that completes when the user is deleted
     */
    Uni<Void> deleteUser(String userId);

    /**
     * Returns all users in the system.
     *
     * @return a Uni emitting the complete list of users (may be empty)
     */
    Uni<List<User>> listUsers();
}
