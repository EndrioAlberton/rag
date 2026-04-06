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

package dev.orion.rag.infrastructure.repository;

import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import dev.orion.rag.infrastructure.persistence.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of UserRepository using Hibernate Reactive Panache.
 * Converts Mutiny {@code Uni<T>} to {@link CompletionStage} at the boundary.
 */
@ApplicationScoped
public class UserRepositoryImpl implements UserRepository {

    /** Panache repository used for all JPA queries and persistence operations on users. */
    @Inject
    UserPanacheRepository panache;

    @Override
    public CompletionStage<User> findById(String id) {
        return panache.find("id", id).<UserEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<User> findByUsername(String username) {
        return panache.find("username", username).<UserEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<User> findByEmail(String email) {
        return panache.find("email", email).<UserEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<User> findByOrionUserHash(String orionUserHash) {
        return panache.find("orionUserHash", orionUserHash).<UserEntity>firstResult()
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<User> persist(User user) {
        UserEntity entity = EntityMapper.toEntity(user);
        return panache.persist(entity)
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<User> merge(User user) {
        UserEntity entity = EntityMapper.toEntity(user);
        return panache.getSession()
                .flatMap(session -> session.merge(entity))
                .map(EntityMapper::toDomain)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> flush() {
        return panache.flush().subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> deleteById(String id) {
        return panache.delete("id", id)
                .map(count -> count > 0)
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<User>> listAll() {
        return panache.listAll()
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList())
                .subscribeAsCompletionStage();
    }
}
