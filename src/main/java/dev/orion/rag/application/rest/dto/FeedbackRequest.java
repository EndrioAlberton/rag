package dev.orion.rag.application.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class FeedbackRequest {
    @NotBlank
    public String userId;

    @NotBlank
    public String conversationId;

    @NotBlank
    public String userMessage;

    @NotBlank
    public String value; // LIKE | DISLIKE
}

