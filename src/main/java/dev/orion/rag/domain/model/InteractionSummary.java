package dev.orion.rag.domain.model;

public class InteractionSummary {
    private String id;
    private String userMessage;
    private String llmResponse;
    private String urgency;
    private String createdAt;

    public InteractionSummary() {}

    public InteractionSummary(String id, String userMessage, String llmResponse,
                              String urgency, String createdAt) {
        this.id = id;
        this.userMessage = userMessage;
        this.llmResponse = llmResponse;
        this.urgency = urgency;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getLlmResponse() { return llmResponse; }
    public void setLlmResponse(String llmResponse) { this.llmResponse = llmResponse; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
