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
import java.util.ArrayList;
import java.util.List;

/**
 * Domain representation of conversation memory — pure Java, no framework annotations.
 */
public class ConversationMemory {

    /** Identifier of the authenticated user owning this memory snapshot. */
    private String userId;
    /** Identifier of the persisted conversation this memory maps to. */
    private String conversationId;
    /** Session token used when the caller is unauthenticated or anonymous. */
    private String session;
    /** Ordered list of messages retained in memory for the current session. */
    private List<ChatMessage> messages;
    /** Timestamp of the most recent message added or the last clear operation. */
    private LocalDateTime lastActivity;
    /** Maximum number of messages to retain; older messages are evicted first. */
    private int maxMessages;

    /**
     * Creates an empty ConversationMemory with default settings (max 50 messages).
     */
    public ConversationMemory() {
        this.messages = new ArrayList<>();
        this.lastActivity = LocalDateTime.now();
        this.maxMessages = 50;
    }

    /**
     * Creates a session-scoped ConversationMemory identified by the given session token.
     *
     * @param session unique session token
     */
    public ConversationMemory(String session) {
        this();
        this.session = session;
        this.conversationId = session;
    }

    /**
     * Creates a user-and-conversation-scoped ConversationMemory.
     *
     * @param userId         identifier of the owning user
     * @param conversationId identifier of the parent conversation
     */
    public ConversationMemory(String userId, String conversationId) {
        this();
        this.userId = userId;
        this.conversationId = conversationId;
        this.session = conversationId;
    }

    /**
     * Creates a session-scoped ConversationMemory with a custom message limit.
     *
     * @param session     unique session token
     * @param maxMessages maximum number of messages to retain
     */
    public ConversationMemory(String session, int maxMessages) {
        this(session);
        this.maxMessages = maxMessages;
    }

    /**
     * Creates a user-and-conversation-scoped ConversationMemory with a custom message limit.
     *
     * @param userId         identifier of the owning user
     * @param conversationId identifier of the parent conversation
     * @param maxMessages    maximum number of messages to retain
     */
    public ConversationMemory(String userId, String conversationId, int
        maxMessages) {
        this(userId, conversationId);
        this.maxMessages = maxMessages;
    }

    /**
     * Acrescenta uma mensagem ao histórico e aplica o limite {@code maxMessages}.
     *
     * @param message mensagem a guardar
     */
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.lastActivity = LocalDateTime.now();
        if (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    /**
     * Returns a plain-text representation of the full conversation history,
     * each line prefixed with the message role.
     *
     * @return history string with one {@code ROLE: content} line per message
     */
    public String getHistory() {
        StringBuilder history = new StringBuilder();
        for (ChatMessage message : messages) {
            history.append(message.getType().name())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        return history.toString();
    }

    /**
     * Returns the most recent {@code count} messages from the history.
     *
     * @param count maximum number of messages to return
     * @return list of at most {@code count} messages, oldest first
     */
    public List<ChatMessage> getLastMessages(int count) {
        int startIndex = Math.max(0, messages.size() - count);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    /**
     * Clears all messages from this memory and resets the last-activity timestamp.
     */
    public void clear() {
        this.messages.clear();
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Returns the total number of messages currently held in memory.
     *
     * @return number of messages
     */
    public int getMessageCount() {
        return this.messages.size();
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

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public String toString() {
        return "ConversationMemory{session='" + session + "', messages=" +
            messages.size() + "}";
    }
}
