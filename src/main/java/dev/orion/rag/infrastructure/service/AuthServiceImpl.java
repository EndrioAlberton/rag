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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.out.AuthService;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.domain.port.out.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;

/**
 * Implementation of AuthService for JWT handling and user synchronization.
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthService {

    /** Repository used to look up and persist users by hash or e-mail. */
    private final UserRepository userRepository;
    /** Service used to create new local user accounts discovered via JWT. */
    private final UserService userService;
    /** Jackson mapper for parsing the Base64-decoded JWT payload. */
    private final ObjectMapper objectMapper;

    /**
     * Creates an AuthServiceImpl with the required collaborators.
     *
     * @param userRepository repository for user persistence
     * @param userService    service for creating new users
     * @param objectMapper   JSON mapper for JWT payload parsing
     */
    @Inject
    public AuthServiceImpl(
            UserRepository userRepository,
            UserService userService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String extractUserHashFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            JsonNode jsonNode = objectMapper.readTree(payload);

            if (!jsonNode.has("c_hash")) {
                throw new IllegalArgumentException(
                        "Hash not found in JWT token. Available claims: "
                                + jsonNode.fieldNames());
            }

            return jsonNode.get("c_hash").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to extract hash from JWT token", e);
        }
    }

    @Override
    public String extractEmailFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            JsonNode jsonNode = objectMapper.readTree(payload);

            if (jsonNode.has("email")) {
                return jsonNode.get("email").asText();
            }

            throw new IllegalArgumentException(
                    "Email not found in JWT token. Available claims: "
                            + jsonNode.fieldNames());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to extract email from JWT token", e);
        }
    }

    @Override
    @WithTransaction
    public Uni<User> syncUserFromJwt(String jwtToken) {
        String orionUserHash = extractUserHashFromJwt(jwtToken);
        String email = extractEmailFromJwt(jwtToken);

        return userRepository.findByOrionUserHash(orionUserHash)
                .onItem().transformToUni(user -> {
                    if (user != null) {
                        return Uni.createFrom().item(user);
                    }

                    return userRepository.findByEmail(email)
                            .onItem().transformToUni(userByEmail -> {
                                if (userByEmail != null) {
                                    userByEmail.setOrionUserHash(orionUserHash);
                                    return userRepository.persist(userByEmail)
                                            .onItem().transformToUni(u ->
                                                    userRepository.flush()
                                                            .replaceWith(u));
                                }

                                String username = email.split("@")[0];

                                return userService.createUser(username, email)
                                        .onItem().transformToUni(newUser -> {
                                            newUser.setOrionUserHash(orionUserHash);
                                            return userRepository.persist(newUser)
                                                    .onItem().transformToUni(u ->
                                                            userRepository.flush()
                                                                    .replaceWith(u));
                                        });
                            });
                });
    }
}
