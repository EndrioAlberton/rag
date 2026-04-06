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

/**
 * Domain representation of a user — pure Java, no framework annotations.
 */
public class User {

    /** Unique identifier assigned by the persistence layer. */
    private String id;
    /** Login username chosen by the user. */
    private String username;
    /** E-mail address of the user; used for identification and notifications. */
    private String email;
    /** Bcrypt hash of the user's local password (may be null for federated identities). */
    private String passwordHash;
    /** Hash of the Orion federated user record, used to detect profile changes. */
    private String orionUserHash;
    /** Timestamp at which the user account was first created. */
    private LocalDateTime createdAt;
    /** Timestamp of the user's most recent successful authentication. */
    private LocalDateTime lastLogin;

    /**
     * Default no-arg constructor required by the persistence and mapping layers.
     */
    public User() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getOrionUserHash() {
        return orionUserHash;
    }

    public void setOrionUserHash(String orionUserHash) {
        this.orionUserHash = orionUserHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', email='" +
            email + "'}";
    }
}
