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

import java.util.concurrent.CompletionStage;

/**
 * Driven port (out) — service contract for the local {@link User} projection.
 *
 * <p>Accounts are owned by Orion Users; this service only mirrors an authenticated user
 * locally so conversations and audit logs can reference them.
 */
public interface UserServicePort {

    /**
     * Creates the local projection of a user with the given username and e-mail.
     *
     * @param username desired login username
     * @param email    user's e-mail address
     * @return a CompletionStage emitting the newly created user
     */
    CompletionStage<User> createUser(String username, String email);
}
