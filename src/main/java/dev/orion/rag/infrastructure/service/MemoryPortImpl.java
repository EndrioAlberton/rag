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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.reactive.mutiny.Mutiny;

import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.ConversationMemory;
import dev.orion.rag.domain.port.out.ConversationRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import dev.orion.rag.infrastructure.repository.ChatMessagePanacheRepository;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementation of {@link MemoryPort} using PostgreSQL + Redis hybrid approach.
 * Uses {@link Mutiny.SessionFactory} for programmatic transaction management and
 * converts to {@link CompletionStage} at the domain boundary.
 */
@ApplicationScoped
public class MemoryPortImpl implements MemoryPort {

    private static final String CONVERSATION_PREFIX = "conversation:";
    private static final String MEMORY_PREFIX = "memory:";

    /** Redis data source used for in-memory session caching. */
    private final ReactiveRedisDataSource reactiveRedisDataSource;
    /** Repository for persistent conversation storage. */
    private final ConversationRepository conversationRepository;
    /** Repository for user entity lookups. */
    private final UserRepository userRepository;
    /** Panache repository for individual chat message persistence. */
    private final ChatMessagePanacheRepository chatMessagePanacheRepository;
    /** Hibernate Reactive session factory for programmatic transaction management. */
    private final Mutiny.SessionFactory sessionFactory;
    /** Maximum number of messages retained in memory per conversation. */
    private final int defaultMaxMessages;
    /** Time-to-live in hours for Redis-cached conversation data. */
    private final int ttlHours;

    /**
     * Creates a MemoryPortImpl with all required dependencies and configuration values.
     *
     * @param reactiveRedisDataSource         Redis data source for session caching
     * @param conversationRepository          repository for persistent conversation storage
     * @param userRepository                  repository for user lookups
     * @param chatMessagePanacheRepository    Panache repository for individual message persistence
     * @param sessionFactory                  Hibernate Reactive session factory
     * @param defaultMaxMessages              maximum messages to retain per conversation
     * @param ttlHours                        time-to-live in hours for Redis-cached data
     */
    @Inject
    public MemoryPortImpl(
            ReactiveRedisDataSource reactiveRedisDataSource,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ChatMessagePanacheRepository chatMessagePanacheRepository,
            Mutiny.SessionFactory sessionFactory,
            @ConfigProperty(name = "memory.default.max-messages", defaultValue = "50") int defaultMaxMessages,
            @ConfigProperty(name = "memory.ttl.hours", defaultValue = "24") int ttlHours) {
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.chatMessagePanacheRepository = chatMessagePanacheRepository;
        this.sessionFactory = sessionFactory;
        this.defaultMaxMessages = defaultMaxMessages;
        this.ttlHours = ttlHours;
    }

    // -------------------------------------------------------------------------
    // saveMessage
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<Void> saveMessage(ChatMessage message) {
        if (message.getUserId() != null && message.getConversationId() != null) {
            return saveMessageHybrid(message);
        }

        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(UUID.randomUUID().toString());
        }

        String key = CONVERSATION_PREFIX + message.getSessionId();
        return getConversationMemoryUni(message.getSessionId())
                .onItem().ifNull()
                .continueWith(() -> new ConversationMemory(message.getSessionId(), defaultMaxMessages))
                .onItem().transform(memory -> {
                    memory.addMessage(message);
                    return memory;
                })
                .chain(memory -> {
                    ReactiveValueCommands<String, ConversationMemory> valueCommands =
                            reactiveRedisDataSource.value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onFailure().invoke(e -> Log.error("Error saving message to Redis: " + e.getMessage(), e))
                .replaceWithVoid()
                .subscribeAsCompletionStage();
    }

    /**
     * Persists a chat message to both Redis (session cache) and the relational database.
     *
     * @param message the chat message to persist
     * @return a {@link CompletionStage} that completes when both stores have been updated
     */
    private CompletionStage<Void> saveMessageHybrid(ChatMessage message) {
        return sessionFactory.withTransaction(session -> {
            Uni<Boolean> accessCheck;
            if (message.getType() == ChatMessage.MessageType.USER && message.getUserId() != null) {
                accessCheck = Uni.createFrom()
                        .completionStage(() -> userRepository.findByOrionUserHash(message.getUserId()))
                        .onItem().ifNull().switchTo(
                                () -> Uni.createFrom().completionStage(
                                        () -> userRepository.findById(message.getUserId())))
                        .onItem().transformToUni(user -> {
                            if (user == null) {
                                return Uni.createFrom().item(false);
                            }
                            return Uni.createFrom().completionStage(
                                    () -> conversationRepository.userHasAccess(
                                            user.getId(), message.getConversationId()));
                        });
            } else {
                accessCheck = Uni.createFrom().item(true);
            }

            return accessCheck
                .chain(hasAccess -> {
                    if (!hasAccess) {
                        return Uni.createFrom().failure(
                                new SecurityException("User does not have access to this conversation"));
                    }
                    return Uni.createFrom()
                        .completionStage(() -> conversationRepository.findById(message.getConversationId()))
                        .onItem().ifNull().failWith(
                                () -> new IllegalArgumentException("Conversation not found"))
                        .chain(conversation -> {
                            message.setConversationId(conversation.getId());
                            message.setSessionId(conversation.getId());
                            message.setId(null);

                            if (message.getType() == ChatMessage.MessageType.USER
                                    && message.getUserId() != null) {
                                return Uni.createFrom()
                                        .completionStage(() -> userRepository.findById(message.getUserId()))
                                        .onItem().ifNull().switchTo(
                                                () -> Uni.createFrom().completionStage(
                                                        () -> userRepository.findByOrionUserHash(
                                                                message.getUserId())))
                                        .chain(user -> {
                                            if (user == null) {
                                                return Uni.createFrom().failure(
                                                        new IllegalArgumentException(
                                                                "User not found: " + message.getUserId()));
                                            }
                                            message.setUserId(user.getId());
                                            message.setUser(user);
                                            return chatMessagePanacheRepository
                                                    .persist(EntityMapper.toEntity(message))
                                                    .chain(() -> Uni.createFrom()
                                                            .completionStage(() -> conversationRepository.flush())
                                                            .replaceWithVoid());
                                        });
                            } else {
                                message.setUserId(null);
                                message.setUser(null);
                                return chatMessagePanacheRepository
                                        .persist(EntityMapper.toEntity(message))
                                        .chain(() -> Uni.createFrom()
                                                .completionStage(() -> conversationRepository.flush())
                                                .replaceWithVoid());
                            }
                        });
                })
                .onFailure().invoke(e -> Log.error("Error saving message to database: " + e.getMessage(), e))
                .replaceWithVoid();
        }).subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------
    // getConversationMemory
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<ConversationMemory> getConversationMemory(String sessionId) {
        return getConversationMemoryUni(sessionId).subscribeAsCompletionStage();
    }

    private Uni<ConversationMemory> getConversationMemoryUni(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveValueCommands<String, ConversationMemory> valueCommands =
                reactiveRedisDataSource.value(ConversationMemory.class);

        return valueCommands.get(key)
                .onItem().invoke(memory -> {
                    if (memory != null) {
                        Log.debug("Retrieved conversation for session: " + sessionId
                                + " with " + memory.getMessageCount() + " messages");
                    } else {
                        Log.debug("No conversation found for session: " + sessionId);
                    }
                })
                .onFailure().invoke(e ->
                        Log.error("Error retrieving conversation from Redis: " + e.getMessage(), e))
                .onFailure().recoverWithNull();
    }

    @Override
    public CompletionStage<ConversationMemory> getConversationMemory(String userId,
            String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        ReactiveValueCommands<String, ConversationMemory> valueCommands =
                reactiveRedisDataSource.value(ConversationMemory.class);

        return valueCommands.get(redisKey)
            .onItem().ifNull().switchTo(() ->
                sessionFactory.withSession(session -> loadConversationMemoryFromDB(conversationId))
                    .chain(memory -> {
                        if (memory != null) {
                            return valueCommands.setex(redisKey, ttlHours * 3600L, memory)
                                    .replaceWith(memory);
                        }
                        return Uni.createFrom().nullItem();
                    }))
            .onFailure().recoverWithNull()
            .subscribeAsCompletionStage();
    }

    /**
     * Loads a {@link ConversationMemory} from the relational database by conversation ID.
     *
     * @param conversationId the conversation identifier
     * @return a {@link io.smallrye.mutiny.Uni} emitting the memory or {@code null} if not found
     */
    private Uni<ConversationMemory> loadConversationMemoryFromDB(String conversationId) {
        return Uni.createFrom().completionStage(() -> conversationRepository.findById(conversationId))
            .onItem().ifNotNull().transform(conversation -> {
                ConversationMemory memory = new ConversationMemory();
                memory.setConversationId(conversationId);
                memory.setSession(conversationId);
                if (conversation.getOwner() != null) {
                    memory.setUserId(conversation.getOwner().getId());
                }
                if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
                    memory.setMessages(new ArrayList<>(conversation.getMessages()));
                } else {
                    memory.setMessages(new ArrayList<>());
                }
                memory.setLastActivity(conversation.getLastActivity() != null
                        ? conversation.getLastActivity()
                        : conversation.getCreatedAt());
                memory.setMaxMessages(defaultMaxMessages);
                return memory;
            })
            .onFailure().recoverWithNull();
    }

    // -------------------------------------------------------------------------
    // getLastMessages
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<List<ChatMessage>> getLastMessages(String sessionId, int count) {
        return getConversationMemoryUni(sessionId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                })
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<ChatMessage>> getLastMessages(String userId,
            String conversationId, int count) {
        return Uni.createFrom().completionStage(() -> getConversationMemory(userId, conversationId))
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                })
                .subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------
    // getHistory
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<String> getHistory(String session) {
        return getConversationMemoryUni(session)
                .onItem().transform(memory -> memory == null ? "" : memory.getHistory())
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<String> getHistory(String userId, String conversationId) {
        return Uni.createFrom().completionStage(() -> getConversationMemory(userId, conversationId))
                .onItem().transform(memory -> memory == null ? "" : memory.getHistory())
                .subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------
    // clearConversation
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<Void> clearConversation(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();
        return keyCommands.del(key)
                .onItem().invoke(() -> Log.info("Cleared conversation for session: " + sessionId))
                .onFailure().invoke(e ->
                        Log.error("Error clearing conversation from Redis: " + e.getMessage(), e))
                .replaceWithVoid()
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> clearConversation(String userId, String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();
        return keyCommands.del(redisKey)
                .onItem().invoke(() ->
                        Log.info("Cleared conversation cache for: " + conversationId))
                .onFailure().invoke(e ->
                        Log.error("Error clearing conversation cache: " + e.getMessage(), e))
                .replaceWithVoid()
                .subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------
    // hasConversation
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<Boolean> hasConversation(String sessionId) {
        return getConversationMemoryUni(sessionId)
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty())
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> hasConversation(String userId, String conversationId) {
        return Uni.createFrom().completionStage(() -> getConversationMemory(userId, conversationId))
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty())
                .subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------
    // setMaxMessages / getMessageCount
    // -------------------------------------------------------------------------

    @Override
    public CompletionStage<Void> setMaxMessages(String sessionId, int maxMessages) {
        return getConversationMemoryUni(sessionId)
                .onItem().ifNotNull().transformToUni(memory -> {
                    memory.setMaxMessages(maxMessages);
                    String key = CONVERSATION_PREFIX + sessionId;
                    ReactiveValueCommands<String, ConversationMemory> valueCommands =
                            reactiveRedisDataSource.value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onItem().invoke(() ->
                        Log.debug("Updated max messages for session: " + sessionId
                                + " to " + maxMessages))
                .replaceWithVoid()
                .subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Integer> getMessageCount(String sessionId) {
        return getConversationMemoryUni(sessionId)
                .onItem().transform(memory -> memory != null ? memory.getMessageCount() : 0)
                .subscribeAsCompletionStage();
    }
}
