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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.ConversationMemory;
import dev.orion.rag.domain.port.out.ConversationRepository;
import dev.orion.rag.domain.port.out.ConversationService;
import dev.orion.rag.domain.port.out.MemoryService;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.infrastructure.persistence.EntityMapper;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementation of MemoryService using PostgreSQL + Redis hybrid approach.
 * PostgreSQL for persistence, Redis for cache/performance.
 */
@ApplicationScoped
public class MemoryServiceImpl implements MemoryService {

    /** Redis key prefix used for anonymous/session-scoped conversation entries. */
    private static final String CONVERSATION_PREFIX = "conversation:";
    /** Redis key prefix used for authenticated conversation memory entries. */
    private static final String MEMORY_PREFIX = "memory:";

    /** Reactive Redis data source used for cache reads and writes. */
    private final ReactiveRedisDataSource reactiveRedisDataSource;
    /** Repository for loading and persisting conversation records from PostgreSQL. */
    private final ConversationRepository conversationRepository;
    /** Service for conversation access-control and lifecycle management. */
    private final ConversationService conversationService;
    /** Repository for looking up user records by ID or Orion hash. */
    private final UserRepository userRepository;
    /** Panache repository for persisting individual chat-message entities. */
    private final ChatMessageRepository chatMessageRepository;
    /** Maximum number of messages to retain per session; configurable via {@code memory.default.max-messages}. */
    private final int defaultMaxMessages;
    /** Number of hours after which a Redis memory entry expires; configurable via {@code memory.ttl.hours}. */
    private final int ttlHours;

    /**
     * Creates a MemoryServiceImpl with all required collaborators and configuration values.
     *
     * @param reactiveRedisDataSource Redis data source for reactive cache access
     * @param conversationRepository  repository for conversation persistence
     * @param conversationService     conversation access-control service
     * @param userRepository          repository for user lookups
     * @param chatMessageRepository   Panache repository for message persistence
     * @param defaultMaxMessages      maximum messages to keep in memory (default 50)
     * @param ttlHours                Redis TTL in hours for memory entries (default 24)
     */
    @Inject
    public MemoryServiceImpl(ReactiveRedisDataSource reactiveRedisDataSource,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository,
            @ConfigProperty(name = "memory.default.max-messages", defaultValue = "50") int defaultMaxMessages,
            @ConfigProperty(name = "memory.ttl.hours", defaultValue = "24") int ttlHours) {
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.defaultMaxMessages = defaultMaxMessages;
        this.ttlHours = ttlHours;
    }

    @Override
    public Uni<Void> saveMessage(ChatMessage message) {
        // If has userId and conversationId, use new hybrid flow
        // Do NOT generate ID here for hybrid flow - let Hibernate generate it
        if (message.getUserId() != null && message.getConversationId() != null) {
            return saveMessageHybrid(message);
        }
        
        // Legacy flow for compatibility (Redis only)
        // Generate ID if not set (for Redis only)
        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(UUID.randomUUID().toString());
        }
        
        String key = CONVERSATION_PREFIX + message.getSessionId();
        return getConversationMemory(message.getSessionId())
                .onItem().ifNull()
                .continueWith(() -> new ConversationMemory(message.getSessionId(), defaultMaxMessages))
                .onItem().transform(memory -> {
                    memory.addMessage(message);
                    return memory;
                })
                .chain(memory -> {
                    ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                            .value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onFailure().invoke(e -> Log.error("Error saving message to Redis: " + e.getMessage(), e))
                .replaceWithVoid();
    }
    
    /**
     * Persists a message to PostgreSQL within a transaction (hybrid flow for authenticated conversations).
     * Verifies user access, resolves the conversation and user, and persists the entity via Panache.
     *
     * @param message the chat message to persist
     * @return a Uni that completes when the message is saved
     */
    @WithTransaction
    public Uni<Void> saveMessageHybrid(ChatMessage message) {
        // 1. Verify user access to conversation (only for USER messages)
        Uni<Boolean> accessCheck;
        if (message.getType() == ChatMessage.MessageType.USER && message.getUserId() != null) {
            accessCheck = conversationService.userHasAccess(message.getUserId(), message.getConversationId());
        } else {
            // ASSISTANT and SYSTEM messages do not need access verification
            accessCheck = Uni.createFrom().item(true);
        }
        
        return accessCheck
            .chain(hasAccess -> {
                if (!hasAccess) {
                    return Uni.createFrom().failure(new SecurityException(
                            "User does not have access to this conversation"));
                }
                
                // 2. Save to PostgreSQL (permanent persistence)
                // Fetch conversation to verify existence and get reference
                return conversationRepository.findById(message.getConversationId())
                    .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversation not found"))
                    .chain(conversation -> {
                        // Configure relationships
                        message.setConversationId(conversation.getId());
                        message.setSessionId(conversation.getId()); // For compatibility
                        
                        // Remove ID if exists to ensure it is a new entity
                        // Hibernate will generate the ID automatically via @GeneratedValue
                        message.setId(null);
                        
                        // Fetch user if it is a user message
                        if (message.getType() == ChatMessage.MessageType.USER && message.getUserId() != null) {
                            // Try to fetch by ID first
                            return userRepository.findById(message.getUserId())
                                .onItem().ifNull().switchTo(() -> {
                                    // If not found by ID, try to fetch by hash (compatibility)
                                    return userRepository.findByOrionUserHash(message.getUserId());
                                })
                                .chain(user -> {
                                    if (user == null) {
                                        return Uni.createFrom().failure(
                                                new IllegalArgumentException(
                                                        "User not found: "
                                                                + message.getUserId()));
                                    }
                                    // Ensure message userId is the database ID, not the hash
                                    message.setUserId(user.getId());
                                    message.setUser(user);
                                    // Persist message directly instead of using cascade
                                    return chatMessageRepository.persist(
                                                    EntityMapper.toEntity(message))
                                        .chain(() -> conversationRepository.flush());
                                });
                        } else {
                            // Assistant or system message - should not have user_id
                            message.setUserId(null);
                            message.setUser(null);
                            // Persistir mensagem diretamente em vez de usar cascade
                            return chatMessageRepository.persist(
                                            EntityMapper.toEntity(message))
                                .chain(() -> conversationRepository.flush());
                        }
                    });
            })
            .onFailure().invoke(e -> Log.error("Error saving message to database: " + e.getMessage(), e))
            .replaceWithVoid();
            // Note: Redis cache will be updated on next read (lazy update)
            // This avoids thread context issues after @WithTransaction
    }

    /**
     * Retrieves the conversation memory for a specific session (backward compatibility).
     *
     * @param sessionId the session identifier
     * @return a Uni containing the conversation memory, or null if not found
     */
    @Override
    public Uni<ConversationMemory> getConversationMemory(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                .value(ConversationMemory.class);

        return valueCommands.get(key)
                .onItem().invoke(memory -> {
                    if (memory != null) {
                        Log.debug("Retrieved conversation for session: " + sessionId +
                                " with " + memory.getMessageCount() + " messages");
                    } else {
                        Log.debug("No conversation found for session: " + sessionId);
                    }
                })
                .onFailure().invoke(e -> Log.error("Error retrieving conversation from Redis: " + e.getMessage(), e))
                .onFailure().recoverWithNull();
    }
    
    /**
     * Retrieves the conversation memory for a specific conversation.
     * Tries Redis cache first, falls back to PostgreSQL if not found.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @return a Uni containing the conversation memory, or null if not found
     */
    @Override
    @WithSession
    public Uni<ConversationMemory> getConversationMemory(String userId, String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        
        // Try to fetch from cache first
        return getConversationMemoryFromRedis(redisKey)
            .onItem().ifNull().switchTo(() -> loadConversationMemoryFromDB(conversationId)
                .chain(memory -> {
                    if (memory != null) {
                        // Save to cache for next queries
                        ReactiveValueCommands<String, ConversationMemory> valueCommands = 
                            reactiveRedisDataSource.value(ConversationMemory.class);
                        return valueCommands.setex(redisKey, ttlHours * 3600L, memory)
                            .replaceWith(memory);
                    }
                    return Uni.createFrom().nullItem();
                }))
            .onFailure().recoverWithNull();
    }
    
    /**
     * Attempts to retrieve a {@link ConversationMemory} from Redis using the given key.
     *
     * @param key Redis key for the conversation memory entry
     * @return a Uni emitting the cached memory, or {@code null} if not found
     */
    private Uni<ConversationMemory> getConversationMemoryFromRedis(String key) {
        ReactiveValueCommands<String, ConversationMemory> valueCommands = 
            reactiveRedisDataSource.value(ConversationMemory.class);
        return valueCommands.get(key);
    }
    
    /**
     * Loads a {@link ConversationMemory} from PostgreSQL for the given conversation ID.
     * Returns an empty memory (no messages) if the conversation exists but has no messages yet.
     *
     * @param conversationId identifier of the conversation to load
     * @return a Uni emitting the populated memory, or {@code null} on failure
     */
    @WithSession
    protected Uni<ConversationMemory> loadConversationMemoryFromDB(String conversationId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNotNull().transform(conversation -> {
                // Criar ConversationMemory mesmo se a conversa não tiver mensagens ainda
                ConversationMemory memory = new ConversationMemory();
                memory.setConversationId(conversationId);
                memory.setSession(conversationId); // Para compatibilidade
                if (conversation.getOwner() != null) {
                    memory.setUserId(conversation.getOwner().getId());
                }
                // Converter Set<ChatMessage> para List<ChatMessage
                // Se não houver mensagens, a lista ficará vazia (válido)
                if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
                    memory.setMessages(new ArrayList<>(conversation.getMessages()));
                } else {
                    memory.setMessages(new ArrayList<>()); // Lista vazia para conversas sem mensagens
                }
                memory.setLastActivity(conversation.getLastActivity() != null 
                    ? conversation.getLastActivity() 
                    : conversation.getCreatedAt());
                memory.setMaxMessages(defaultMaxMessages);
                return memory;
            })
            .onFailure().recoverWithNull();
    }

    /**
     * Gets the last N messages from a conversation (backward compatibility).
     *
     * @param sessionId the session identifier
     * @param count     the number of messages to retrieve
     * @return a Uni containing list of the last N messages
     */
    @Override
    public Uni<List<ChatMessage>> getLastMessages(String sessionId, int count) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                });
    }
    
    /**
     * Gets the last N messages from a conversation.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @param count     the number of messages to retrieve
     * @return a Uni containing list of the last N messages
     */
    @Override
    public Uni<List<ChatMessage>> getLastMessages(String userId, String conversationId, int count) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                });
    }

    /**
     * Gets the full conversation history as a single string (backward compatibility).
     *
     * @param session the session identifier
     * @return a Uni containing the conversation history as a string
     */
    @Override
    public Uni<String> getHistory(String session) {
        return getConversationMemory(session)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return "";
                    }
                    return memory.getHistory();
                });
    }
    
    /**
     * Gets the full conversation history as a single string.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @return a Uni containing the conversation history as a string
     */
    @Override
    public Uni<String> getHistory(String userId, String conversationId) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return "";
                    }
                    return memory.getHistory();
                });
    }

    @Override
    public Uni<Void> clearConversation(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();

        return keyCommands.del(key)
                .onItem().invoke(() -> Log.info("Cleared conversation for session: " + sessionId))
                .onFailure().invoke(e -> Log.error("Error clearing conversation from Redis: " + e.getMessage(), e))
                .replaceWithVoid();
    }
    
    @Override
    public Uni<Void> clearConversation(String userId, String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();
        
        // Limpar apenas do cache Redis (mensagens permanecem no PostgreSQL)
        return keyCommands.del(redisKey)
                .onItem().invoke(() -> Log.info("Cleared conversation cache for: " + conversationId))
                .onFailure().invoke(e -> Log.error("Error clearing conversation cache: " + e.getMessage(), e))
                .replaceWithVoid();
    }

    @Override
    public Uni<Boolean> hasConversation(String sessionId) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty());
    }
    
    @Override
    public Uni<Boolean> hasConversation(String userId, String conversationId) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty());
    }

    @Override
    public Uni<Void> setMaxMessages(String sessionId, int maxMessages) {
        return getConversationMemory(sessionId)
                .onItem().ifNotNull().transformToUni(memory -> {
                    memory.setMaxMessages(maxMessages);

                    // Save updated memory back to Redis
                    String key = CONVERSATION_PREFIX + sessionId;
                    ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                            .value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onItem()
                .invoke(() -> Log.debug("Updated max messages for session: " + sessionId + " to " + maxMessages))
                .replaceWithVoid();
    }

    @Override
    public Uni<Integer> getMessageCount(String sessionId) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> memory != null ? memory.getMessageCount() : 0);
    }
}
