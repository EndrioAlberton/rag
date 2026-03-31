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

/**
 * Driven port (out) for authentication and JWT handling.
 */
public interface AuthPort {

    /**
     * Extracts user hash from JWT token and synchronizes/creates local user.
     *
     * @param jwtToken The JWT token from Orion Users
     * @return The synchronized local user
     */
    Uni<User> syncUserFromJwt(String jwtToken);

    /**
     * Extracts user hash from JWT token.
     *
     * @param jwtToken The JWT token from Orion Users
     * @return The user hash (orionUserHash)
     */
    String extractUserHashFromJwt(String jwtToken);

    /**
     * Extracts email from JWT token.
     *
     * @param jwtToken The JWT token from Orion Users
     * @return The user email
     */
    String extractEmailFromJwt(String jwtToken);
}
