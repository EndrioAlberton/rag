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
 * Driven port (out) — repository contract for {@link User} persistence.
 */
public interface UserRepository {

    /**
     * Finds a user by their unique identifier.
     *
     * @param id user identifier
     * @return a Uni emitting the user, or {@code null} if not found
     */
    Uni<User> findById(String id);

    /**
     * Finds a user by their login username.
     *
     * @param username login username
     * @return a Uni emitting the user, or {@code null} if not found
     */
    Uni<User> findByUsername(String username);

    /**
     * Finds a user by their e-mail address.
     *
     * @param email e-mail address
     * @return a Uni emitting the user, or {@code null} if not found
     */
    Uni<User> findByEmail(String email);

    /**
     * Finds a user by the Orion federated user hash.
     *
     * @param orionUserHash hash of the Orion user record
     * @return a Uni emitting the user, or {@code null} if not found
     */
    Uni<User> findByOrionUserHash(String orionUserHash);

    /**
     * Persists a new or updated user.
     *
     * @param user the user to persist
     * @return a Uni emitting the persisted user (with any generated fields populated)
     */
    Uni<User> persist(User user);

    /**
     * Flushes any pending changes to the underlying persistence store.
     *
     * @return a Uni that completes when the flush is done
     */
    Uni<Void> flush();

    /**
     * Deletes the user with the given identifier.
     *
     * @param id user identifier
     * @return a Uni emitting {@code true} if deleted, {@code false} if not found
     */
    Uni<Boolean> deleteById(String id);

    /**
     * Returns all users in the system.
     *
     * @return a Uni emitting the complete list of users (may be empty)
     */
    Uni<List<User>> listAll();
}
