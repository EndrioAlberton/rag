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

package dev.orion.rag.application.rest;

import dev.orion.rag.application.rest.dto.ChatbotRequest;
import dev.orion.rag.application.rest.dto.ContextResponse;
import dev.orion.rag.application.rest.dto.ConversationRequest;
import dev.orion.rag.domain.model.Conversation;
import dev.orion.rag.domain.model.ConversationMemory;
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.domain.port.out.AuthPort;
import dev.orion.rag.domain.port.out.ConversationServicePort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Main REST controller for RAG-powered chat, context retrieval and conversation management.
 * Converts between the domain's {@link java.util.concurrent.CompletionStage}/
 * {@link java.util.concurrent.Flow.Publisher} and Mutiny types at the application boundary.
 *
 * <p>Every endpoint requires an authenticated user; account management lives in Orion Users.
 */
@Path("/ai")
public class RagController {

    /** Use case that handles streaming chatbot responses within a conversation. */
    private final ChatbotPort chatbotUseCase;
    /** Port for embedding-based retrieval of course context. */
    private final EmbeddingRepository embeddingRepository;
    /** Port that manages per-conversation memory. */
    private final MemoryPort memoryPort;
    /** Domain service for conversation management. */
    private final ConversationServicePort conversationServicePort;
    /** Port for JWT authentication and user resolution. */
    private final AuthPort authPort;
    /** Port for persisting request/response audit logs. */
    private final RequestLogPort requestLogPort;

    /** JAX-RS request context used to access HTTP headers and attributes. */
    @Context
    ContainerRequestContext requestContext;

    /**
     * Creates a RagController with all required domain ports.
     *
     * @param chatbotUseCase           streaming chatbot use case
     * @param embeddingRepository      embedding search repository
     * @param memoryPort               conversation memory port
     * @param conversationServicePort  conversation management service
     * @param authPort                 JWT authentication port
     * @param requestLogPort           audit log port
     */
    @Inject
    public RagController(ChatbotPort chatbotUseCase,
            EmbeddingRepository embeddingRepository,
            MemoryPort memoryPort,
            ConversationServicePort conversationServicePort,
            AuthPort authPort,
            RequestLogPort requestLogPort) {
        this.chatbotUseCase = chatbotUseCase;
        this.embeddingRepository = embeddingRepository;
        this.memoryPort = memoryPort;
        this.conversationServicePort = conversationServicePort;
        this.authPort = authPort;
        this.requestLogPort = requestLogPort;
    }

    /**
     * Extracts the JWT token from the current request context and synchronises the user
     * with the domain, returning the resolved {@link User}.
     *
     * @return a {@link io.smallrye.mutiny.Uni} emitting the synchronised user
     */
    private Uni<User> syncUserFromRequest() {
        String jwtToken = (String) requestContext.getProperty("jwt.token");
        if (jwtToken == null) {
            Log.warn("JWT token not found in request context");
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "JWT token não encontrado no contexto da requisição"));
        }
        return Uni.createFrom().completionStage(() -> authPort.syncUserFromJwt(jwtToken))
                .onFailure().invoke(e -> Log.error("Failed to synchronize user from JWT token", e));
    }

    /**
     * Streams a chatbot response for an authenticated user within a conversation.
     *
     * @param request the chatbot request containing the conversation ID and prompt
     * @return server-sent event stream of response tokens
     */
    @POST
    @Path("/chatbot")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chatbot(@Valid ChatbotRequest request) {
        String jwtToken = (String) requestContext.getProperty("jwt.token");

        if (jwtToken != null) {
            Log.info("Chatbot POST - Conversation: " + request.conversationId);
            return syncUserFromRequest()
                .onItem().transformToMulti(syncedUser -> {
                    String syncedUserId = syncedUser.getId();
                    String syncedUserName = syncedUser.getUsername();
                    String syncedEmail = syncedUser.getEmail();
                    return Uni.createFrom()
                            .completionStage(() -> conversationServicePort
                                    .userHasAccess(syncedUserId, request.conversationId))
                            .onItem().transformToMulti(hasAccess -> {
                                if (!hasAccess) {
                                    Log.warn("Access denied for user " + syncedUserId
                                            + " to conversation " + request.conversationId);
                                    return Multi.createFrom().failure(
                                            new SecurityException("Acesso negado"));
                                }
                                return Multi.createFrom().publisher(
                                        chatbotUseCase.execute(
                                                syncedUserId, request.conversationId,
                                                request.prompt,
                                                syncedUserName, syncedEmail));
                            })
                            .onFailure().recoverWithMulti(e -> {
                                String msg = e instanceof SecurityException
                                        ? "Erro: Acesso negado à conversa"
                                        : "Erro: " + (e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                                return Multi.createFrom().item("data: " + msg + "\n\n");
                            });
                })
                .onFailure().invoke(e -> Log.error("Error processing chatbot request", e))
                .onFailure().recoverWithMulti(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + msg + "\n\n");
                });
        } else {
            Log.warn("JWT token not found in POST /chatbot request");
            return Multi.createFrom().item("data: Erro: Token de autenticação não encontrado\n\n");
        }
    }

    /**
     * GET variant of the chatbot stream, for clients that cannot POST an SSE request
     * (e.g. the browser's native {@code EventSource}).
     *
     * @param userId         authenticated user ID
     * @param conversationId conversation to resume
     * @param prompt         the user's prompt text
     * @return server-sent event stream of response tokens
     */
    @GET
    @Path("/chatbot")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RolesAllowed("user")
    public Multi<String> chatbotGet(
            @QueryParam("userId") @NotBlank String userId,
            @QueryParam("conversationId") @NotBlank String conversationId,
            @QueryParam("prompt") @NotBlank String prompt) {
        Log.info("Chatbot GET User: " + userId + ", Conversation: " + conversationId);
        return syncUserFromRequest()
            .onItem().transformToMulti(syncedUser -> {
                String syncedUserId = syncedUser.getId();
                String syncedUserName = syncedUser.getUsername();
                String syncedEmail = syncedUser.getEmail();
                return Uni.createFrom()
                        .completionStage(() -> conversationServicePort
                                .userHasAccess(syncedUserId, conversationId))
                        .onItem().transformToMulti(hasAccess -> {
                            if (!hasAccess) {
                                Log.warn("Access denied for user " + syncedUserId
                                        + " to conversation " + conversationId);
                                return Multi.createFrom().failure(
                                        new SecurityException("Acesso negado"));
                            }
                            return Multi.createFrom().publisher(
                                    chatbotUseCase.execute(
                                            syncedUserId, conversationId, prompt,
                                            syncedUserName, syncedEmail));
                        })
                        .onFailure().recoverWithMulti(e -> {
                            String msg = e instanceof SecurityException
                                    ? "Erro: Acesso negado à conversa"
                                    : "Erro: " + (e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                            return Multi.createFrom().item("data: " + msg + "\n\n");
                        });
            })
            .onFailure().invoke(e -> Log.error("Error processing chatbot request", e))
            .onFailure().recoverWithMulti(e -> {
                String msg = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                return Multi.createFrom().item("data: Erro: " + msg + "\n\n");
            });
    }

    /**
     * Returns the most relevant RAG context chunks for a given prompt. Exposes the retrieval
     * layer on its own, so a response can be traced back to the documents that grounded it.
     *
     * @param session    the session or course identifier
     * @param prompt     the query text used for similarity search
     * @param maxResults maximum number of context chunks to return (capped 1–20)
     * @return the retrieved context response
     */
    @GET
    @Path("/context")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<ContextResponse> getContext(
            @QueryParam("session") @NotBlank String session,
            @QueryParam("prompt") @NotBlank String prompt,
            @QueryParam("maxResults") @DefaultValue("5") int maxResults) {
        Log.info("Context retrieval Session: " + session + ", prompt: " + prompt);
        RagQuery query = new RagQuery(prompt, Math.min(Math.max(maxResults, 1), 20), 0.5);
        return Uni.createFrom().completionStage(() -> embeddingRepository.searchChunks(query))
                .map(ragResponse -> new ContextResponse(
                        ragResponse.getQuery(),
                        ragResponse.getContexts(),
                        ragResponse.getScore()))
                .onItem().ifNull().continueWith(
                        () -> new ContextResponse(prompt, List.of(), 0.0));
    }

    /**
     * Returns the stored conversation state for the authenticated user.
     *
     * @param userId         authenticated user ID
     * @param conversationId conversation identifier
     * @return the current {@link ConversationMemory}, or null if not found
     */
    @GET
    @Path("/memory")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<ConversationMemory> getMemory(
            @QueryParam("userId") @NotBlank String userId,
            @QueryParam("conversationId") @NotBlank String conversationId) {
        Log.info("Memory User: " + userId + ", Conversation: " + conversationId);
        return syncUserFromRequest()
            .onItem().transformToUni(syncedUser ->
                    Uni.createFrom().completionStage(() ->
                            memoryPort.getConversationMemory(
                                    syncedUser.getOrionUserHash(), conversationId)));
    }

    /**
     * Exports all request logs as a CSV file attachment.
     *
     * @return HTTP response with CSV content and attachment header
     */
    @GET
    @Path("/logs/export")
    @Produces("text/csv")
    @RolesAllowed("user")
    public Uni<Response> exportLogsToCsv() {
        return Uni.createFrom().completionStage(() -> requestLogPort.exportToCsv())
                .map(csv -> Response.ok(csv)
                        .header("Content-Disposition", "attachment; filename=\"request_logs.csv\"")
                        .build());
    }

    // =========================================================================
    // Conversation endpoints
    // =========================================================================

    /**
     * Creates a new conversation for the authenticated user.
     *
     * @param userId  path variable (ignored; resolved from JWT)
     * @param request conversation creation request with title
     * @return the created {@link Conversation}
     */
    @POST
    @Path("/users/{userId}/conversations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> createConversation(
            @PathParam("userId") String userId,
            @Valid ConversationRequest request) {
        Log.info("Creating conversation for user: " + userId);
        return syncUserFromRequest()
                .onItem().transformToUni(syncedUser ->
                        Uni.createFrom().completionStage(() ->
                                conversationServicePort.createConversation(
                                        syncedUser.getOrionUserHash(), request.title)));
    }

    /**
     * Returns all conversations belonging to the authenticated user.
     *
     * @param userId path variable (ignored; resolved from JWT)
     * @return list of {@link Conversation} objects for the user
     */
    @GET
    @Path("/users/{userId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<List<Conversation>> getUserConversations(
            @PathParam("userId") String userId) {
        Log.info("Getting conversations for user: " + userId);
        return syncUserFromRequest()
                .onItem().transformToUni(syncedUser ->
                        Uni.createFrom().completionStage(() ->
                                conversationServicePort.getUserConversations(
                                        syncedUser.getOrionUserHash())));
    }

    /**
     * Retrieves a conversation by its unique identifier.
     *
     * @param conversationId the conversation's unique identifier
     * @return the matching {@link Conversation}
     */
    @GET
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> getConversation(
            @PathParam("conversationId") String conversationId) {
        Log.info("Getting conversation: " + conversationId);
        return Uni.createFrom().completionStage(
                () -> conversationServicePort.getConversation(conversationId));
    }

    /**
     * Updates the title of a conversation owned by the authenticated user.
     *
     * @param conversationId conversation identifier
     * @param request        body with the new title
     * @return the updated conversation
     */
    @PATCH
    @Path("/conversations/{conversationId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> updateConversation(
            @PathParam("conversationId") String conversationId,
            @Valid ConversationRequest request) {
        Log.info("Updating conversation title: " + conversationId);
        return syncUserFromRequest()
                .onItem().transformToUni(syncedUser ->
                        Uni.createFrom().completionStage(() ->
                                conversationServicePort.updateConversationTitle(
                                        conversationId, syncedUser.getId(), request.title)));
    }

    /**
     * Deletes a conversation owned by the authenticated user.
     *
     * @param conversationId the conversation to delete
     * @param userId         query param (used for logging; authorisation is JWT-based)
     * @return HTTP 200 OK on success
     */
    @DELETE
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Response> deleteConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("userId") @NotBlank String userId) {
        Log.info("Deleting conversation " + conversationId + " by user " + userId);
        return syncUserFromRequest()
                .onItem().transformToUni(syncedUser ->
                        Uni.createFrom().completionStage(() ->
                                conversationServicePort.deleteConversation(
                                        conversationId, syncedUser.getId()))
                                .replaceWith(Response.ok().build()));
    }
}
