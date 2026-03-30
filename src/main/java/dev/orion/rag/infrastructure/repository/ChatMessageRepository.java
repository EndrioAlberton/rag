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

import dev.orion.rag.infrastructure.persistence.ChatMessageEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for ChatMessageEntity using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class ChatMessageRepository implements
    PanacheRepositoryBase<ChatMessageEntity, String> {
    
    /**
     * Persists a new chat message entity and returns the managed entity with the generated ID.
     *
     * @param entity the chat message entity to persist
     * @return a Uni emitting the persisted entity
     */
    public Uni<ChatMessageEntity> persist(ChatMessageEntity entity) {
        return PanacheRepositoryBase.super.persist(entity);
    }
}

