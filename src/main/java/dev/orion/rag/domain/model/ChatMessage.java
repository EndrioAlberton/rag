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
 * Domain representation of a chat message — pure Java, no framework annotations.
 */
public class ChatMessage {

    /** Unique identifier of this message, assigned by the persistence layer. */
    private String id;
    /** Session token that this message belongs to (used in anonymous/anonymous flows). */
    private String sessionId;
    /** Identifier of the authenticated user who sent or received the message. */
    private String userId;
    /** Identifier of the {@link Conversation} this message is part of. */
    private String conversationId;
    /** Parent conversation object (may be null if only the ID is populated). */
    private Conversation conversation;
    /** User domain object associated with this message. */
    private User user;
    /** Textual content of the message as produced by the user or the AI. */
    private String content;
    /** Semantic role of this message within the conversation. */
    private MessageType type;
    /** Timestamp at which the message was created; initialised to the current time. */
    private LocalDateTime timestamp;

    /**
     * Default constructor; sets the timestamp to the current date and time.
     */
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Creates a session-scoped chat message.
     *
     * @param sessionId identifier of the session this message belongs to
     * @param content   textual content of the message
     * @param type      semantic role (USER, ASSISTANT or SYSTEM)
     */
    public ChatMessage(String sessionId, String content, MessageType type) {
        this();
        this.sessionId = sessionId;
        this.content = content;
        this.type = type;
    }

    /**
     * Creates a user-scoped chat message linked to a specific conversation.
     *
     * @param userId         identifier of the user author
     * @param conversationId identifier of the parent conversation
     * @param content        textual content of the message
     * @param type           semantic role (USER, ASSISTANT or SYSTEM)
     */
    public ChatMessage(String userId, String conversationId, String content,
        MessageType type) {
        this();
        this.userId = userId;
        this.conversationId = conversationId;
        this.sessionId = conversationId;
        this.content = content;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Semantic roles a chat message can take within a conversation.
     */
    public enum MessageType {
        /** Message produced by the human user. */
        USER,
        /** Message produced by the AI assistant. */
        ASSISTANT,
        /** System-level instruction or context injected into the conversation. */
        SYSTEM
    }

    @Override
    public String toString() {
        return "ChatMessage{id='" + id + "', type=" + type + '}';
    }
}
