/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Represents a chat message in the conversation memory.
 */
@Entity
@Table(name = "chat_messages")
@Getter @Setter @ToString(exclude = {"conversation", "user"})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    // Fields for compatibility with existing code
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "conversation_id")
    private String conversationId;
    
    // Relacionamentos JPA
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
    private Conversation conversation;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(String sessionId, String content, MessageType type) {
        this();
        this.sessionId = sessionId;
        this.content = content;
        this.type = type;
    }
    
    public ChatMessage(String userId, String conversationId, String content, MessageType type) {
        this();
        this.userId = userId;
        this.conversationId = conversationId;
        this.sessionId = conversationId; // Para compatibilidade
        this.content = content;
        this.type = type;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public enum MessageType {
        USER, ASSISTANT, SYSTEM
    }

}
