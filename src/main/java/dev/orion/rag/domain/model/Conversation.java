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

package dev.orion.rag.domain.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Domain representation of a conversation — pure Java, no framework annotations.
 */
public class Conversation {

    /** Unique identifier of this conversation, assigned by the persistence layer. */
    private String id;
    /** Human-readable title summarising the conversation topic. */
    private String title;
    /** User who owns and initiated this conversation. */
    private User owner;
    /** Date and time when the conversation was first created. */
    private LocalDateTime createdAt;
    /** Date and time of the most recent message or activity in this conversation. */
    private LocalDateTime lastActivity;
    /** Ordered set of chat messages that make up this conversation. */
    private Set<ChatMessage> messages = new HashSet<>();

    /**
     * Default no-arg constructor required by the persistence and mapping layers.
     */
    public Conversation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public Set<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(Set<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "Conversation{id='" + id + "', title='" + title + "'}";
    }
}
