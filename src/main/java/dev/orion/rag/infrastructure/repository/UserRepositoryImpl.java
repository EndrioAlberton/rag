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
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of UserRepository using Hibernate Reactive Panache.
 * Delegates to UserPanacheRepository for JPA operations and converts
 * between UserEntity (JPA) and User (domain) via EntityMapper.
 */
@ApplicationScoped
public class UserRepositoryImpl implements UserRepository {

    /** Panache repository used for all JPA queries and persistence operations on users. */
    @Inject
    UserPanacheRepository panache;

    @Override
    public Uni<User> findById(String id) {
        return panache.find("id", id).<UserEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<User> findByUsername(String username) {
        return panache.find("username", username).<UserEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<User> findByEmail(String email) {
        return panache.find("email", email).<UserEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<User> findByOrionUserHash(String orionUserHash) {
        return panache.find("orionUserHash",
            orionUserHash).<UserEntity>firstResult()
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<User> persist(User user) {
        UserEntity entity = EntityMapper.toEntity(user);
        return panache.persist(entity)
                .map(EntityMapper::toDomain);
    }

    @Override
    public Uni<Void> flush() {
        return panache.flush();
    }

    @Override
    public Uni<Boolean> deleteById(String id) {
        return panache.delete("id", id).map(count -> count > 0);
    }

    @Override
    public Uni<List<User>> listAll() {
        return panache.listAll()
                .map(entities -> entities.stream()
                        .map(EntityMapper::toDomain)
                        .toList());
    }
}
