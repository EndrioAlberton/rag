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

import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.domain.port.out.UserServicePort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link UserServicePort}.
 * Uses {@link Mutiny.SessionFactory} for programmatic transaction management.
 */
@ApplicationScoped
public class UserServiceImpl implements UserServicePort {

    /** Repository for user entity persistence. */
    private final UserRepository userRepository;
    /** Hibernate Reactive session factory for programmatic transaction management. */
    private final Mutiny.SessionFactory sessionFactory;

    /**
     * Creates a UserServiceImpl with all required dependencies.
     *
     * @param userRepository repository for user persistence
     * @param sessionFactory Hibernate Reactive session factory
     */
    @Inject
    public UserServiceImpl(UserRepository userRepository, Mutiny.SessionFactory sessionFactory) {
        this.userRepository = userRepository;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletionStage<User> createUser(String username, String email) {
        return sessionFactory.withTransaction(session ->
            Uni.createFrom().completionStage(() -> userRepository.findByUsername(username))
                .onItem().transformToUni(existingUser -> {
                    if (existingUser != null) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("Username já existe"));
                    }
                    return Uni.createFrom().completionStage(() -> userRepository.findByEmail(email))
                            .onItem().transformToUni(existingEmail -> {
                                if (existingEmail != null) {
                                    return Uni.createFrom().failure(
                                            new IllegalArgumentException("Email já existe"));
                                }
                                User user = new User();
                                user.setUsername(username);
                                user.setEmail(email);
                                return Uni.createFrom()
                                        .completionStage(() -> userRepository.persist(user))
                                        .onItem().transformToUni(persisted ->
                                                Uni.createFrom()
                                                        .completionStage(() -> userRepository.flush())
                                                        .replaceWith(persisted));
                            });
                })
        ).subscribeAsCompletionStage();
    }

}
