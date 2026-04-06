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
import dev.orion.rag.domain.port.out.AuthPort;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.domain.port.out.UserServicePort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Base64;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link AuthPort} for JWT handling and user synchronization.
 * Uses {@link Mutiny.SessionFactory} for programmatic transaction management.
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthPort {

    /** Repository for user lookups. */
    private final UserRepository userRepository;
    /** Domain service used to create or synchronise users on first login. */
    private final UserServicePort userServicePort;
    /** Jackson mapper used for JWT payload deserialization. */
    private final ObjectMapper objectMapper;
    /** Hibernate Reactive session factory for programmatic transaction management. */
    private final Mutiny.SessionFactory sessionFactory;

    /**
     * Creates an AuthServiceImpl with all required dependencies.
     *
     * @param userRepository  repository for user lookups
     * @param userServicePort domain service for user creation/synchronisation
     * @param objectMapper    Jackson mapper for JWT payload parsing
     * @param sessionFactory  Hibernate Reactive session factory
     */
    @Inject
    public AuthServiceImpl(
            UserRepository userRepository,
            UserServicePort userServicePort,
            ObjectMapper objectMapper,
            Mutiny.SessionFactory sessionFactory) {
        this.userRepository = userRepository;
        this.userServicePort = userServicePort;
        this.objectMapper = objectMapper;
        this.sessionFactory = sessionFactory;
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
                        "Hash not found in JWT token. Available claims: " + jsonNode.fieldNames());
            }
            return jsonNode.get("c_hash").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract hash from JWT token", e);
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
                    "Email not found in JWT token. Available claims: " + jsonNode.fieldNames());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract email from JWT token", e);
        }
    }

    @Override
    public CompletionStage<User> syncUserFromJwt(String jwtToken) {
        String orionUserHash = extractUserHashFromJwt(jwtToken);
        String email = extractEmailFromJwt(jwtToken);

        return sessionFactory.withTransaction(session ->
            Uni.createFrom().completionStage(() -> userRepository.findByOrionUserHash(orionUserHash))
                .onItem().transformToUni(user -> {
                    if (user != null) {
                        return Uni.createFrom().item(user);
                    }
                    return Uni.createFrom().completionStage(() -> userRepository.findByEmail(email))
                            .onItem().transformToUni(userByEmail -> {
                                if (userByEmail != null) {
                                    userByEmail.setOrionUserHash(orionUserHash);
                                    return Uni.createFrom()
                                            .completionStage(() -> userRepository.persist(userByEmail))
                                            .onItem().transformToUni(u ->
                                                    Uni.createFrom()
                                                            .completionStage(() -> userRepository.flush())
                                                            .replaceWith(u));
                                }
                                String username = email.split("@")[0];
                                return Uni.createFrom()
                                        .completionStage(() -> userServicePort.createUser(username, email))
                                        .onItem().transformToUni(newUser -> {
                                            newUser.setOrionUserHash(orionUserHash);
                                            return Uni.createFrom()
                                                    .completionStage(() -> userRepository.persist(newUser))
                                                    .onItem().transformToUni(u ->
                                                            Uni.createFrom()
                                                                    .completionStage(() -> userRepository.flush())
                                                                    .replaceWith(u));
                                        });
                            });
                })
        ).subscribeAsCompletionStage();
    }
}
