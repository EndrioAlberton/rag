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
import dev.orion.rag.application.rest.dto.UserRequest;
import dev.orion.rag.domain.model.Conversation;
import dev.orion.rag.domain.model.ConversationMemory;
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.in.AskQuestionPort;
import dev.orion.rag.domain.port.in.ChatbotPort;
import dev.orion.rag.domain.port.out.AuthPort;
import dev.orion.rag.domain.port.out.ConversationServicePort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import dev.orion.rag.domain.port.out.UserServicePort;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.List;

/**
 * Main REST controller for RAG-powered chat, context retrieval, conversation management and user operations.
 */
@Path("/ai")
public class RagController {

    /** Port for multi-turn chatbot interactions backed by conversation memory and RAG. */
    private final ChatbotPort chatbotUseCase;
    /** Port for single-turn question answering using RAG without memory. */
    private final AskQuestionPort askQuestionUseCase;
    /** Repository for vector-similarity search against the ingested document corpus. */
    private final EmbeddingRepository embeddingRepository;
    /** Port for loading and persisting per-session or per-conversation memory. */
    private final MemoryPort memoryPort;
    /** Port for user CRUD operations. */
    private final UserServicePort userServicePort;
    /** Port for conversation lifecycle management. */
    private final ConversationServicePort conversationServicePort;
    /** Port for JWT parsing and Orion user synchronisation. */
    private final AuthPort authPort;
    /** Port for persisting and exporting request/response audit logs. */
    private final RequestLogPort requestLogPort;

    /** JAX-RS context that carries per-request properties such as the JWT token. */
    @Context
    ContainerRequestContext requestContext;

    /**
     * Constructs the controller with all required collaborators.
     *
     * @param chatbotUseCase       chatbot port
     * @param askQuestionUseCase   ask-question port
     * @param embeddingRepository  embedding store repository
     * @param memoryPort           conversation memory port
     * @param userServicePort      user management port
     * @param conversationServicePort conversation management port
     * @param authPort             authentication and JWT port
     * @param requestLogPort       audit-log persistence port
     */
    @Inject
    public RagController(ChatbotPort chatbotUseCase,
            AskQuestionPort askQuestionUseCase,
            EmbeddingRepository embeddingRepository,
            MemoryPort memoryPort,
            UserServicePort userServicePort,
            ConversationServicePort conversationServicePort,
            AuthPort authPort,
            RequestLogPort requestLogPort) {

        this.chatbotUseCase = chatbotUseCase;
        this.askQuestionUseCase = askQuestionUseCase;
        this.embeddingRepository = embeddingRepository;
        this.memoryPort = memoryPort;
        this.userServicePort = userServicePort;
        this.conversationServicePort = conversationServicePort;
        this.authPort = authPort;
        this.requestLogPort = requestLogPort;
    }
    
    /**
     * Sincroniza o usuário a partir do JWT token automaticamente.
     * Se o usuário não existir, ele será criado.
     * @return Uni com o usuário sincronizado, ou falha se o token não estiver presente
     */
    private Uni<User> syncUserFromRequest() {
        String jwtToken = (String) requestContext.getProperty("jwt.token");
        
        if (jwtToken == null) {
            Log.warn("JWT token not found in request context");
            return Uni.createFrom()
                    .failure(
                            new IllegalArgumentException(
                                    "JWT token não encontrado no contexto da "
                                            + "requisição"));
        }

        return authPort
                .syncUserFromJwt(jwtToken)
                .onFailure()
                .invoke(e -> Log.error("Failed to synchronize user from JWT token", e));
    }

    /**
     * Chatbot com corpo JSON (POST); exige JWT e devolve resposta em SSE.
     */
    @POST
    @Path("/chatbot")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chatbot(@Valid ChatbotRequest request) {
        // Extrair userId do token JWT quando disponível
        String jwtToken = (String) requestContext.getProperty("jwt.token");
        
        if (jwtToken != null) {
            // Novo formato: usar token JWT para extrair userId
            Log.info("Chatbot POST - Conversation: " + request.conversationId);
            
            // Sincronizar usuário do JWT token e extrair ID
            return syncUserFromRequest()
                .onItem().transformToMulti(syncedUser -> {
                    String syncedUserId = syncedUser.getId();
                    String syncedUserName = syncedUser.getUsername();
                    String syncedEmail = syncedUser.getEmail();
                    // Verificar acesso antes de processar (usando o ID do usuário)
                    return conversationServicePort
                            .userHasAccess(syncedUserId, request.conversationId)
                            .onItem()
                            .transformToMulti(
                                    hasAccess -> {
                                        if (!hasAccess) {
                                            Log.warn(
                                                    "Access denied for user "
                                                            + syncedUserId
                                                            + " to conversation "
                                                            + request.conversationId);
                                            return Multi.createFrom()
                                                    .failure(
                                                            new SecurityException(
                                                                    "Acesso "
                                                                            + "negado"));
                                        }
                                        return Multi.createFrom()
                                                .publisher(
                                                        chatbotUseCase
                                                                .executeWithPhone(
                                                                        syncedUserId,
                                                                        request
                                                                                .conversationId,
                                                                        request
                                                                                .prompt,
                                                                        null,
                                                                        syncedUserName,
                                                                        syncedEmail));
                                    })
                            .onFailure()
                            .recoverWithMulti(
                                    e -> {
                                        String errorMessage =
                                                e instanceof SecurityException
                                                        ? "Erro: Acesso negado à conversa"
                                                        : "Erro: "
                                                                + (e.getMessage()
                                                                                != null
                                                                        ? e.getMessage()
                                                                        : "Erro desconhecido");
                                        return Multi.createFrom()
                                                .item("data: " + errorMessage + "\n\n");
                                    });
                })
                .onFailure()
                .invoke(e -> Log.error("Error processing chatbot request", e))
                .onFailure().recoverWithMulti(e -> {
                    // Tratar falhas na sincronização do usuário
                    String errorMessage = e.getMessage() != null ?
                        e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + errorMessage
                        + "\n\n");
                });
        } else {
            // Token não presente - retornar erro
            Log.warn("JWT token not found in POST /chatbot request");
            return Multi.createFrom()
                    .item(
                            "data: Erro: Token de autenticação não encontrado\n\n");
        }
    }

    /**
     * Variante legada do chatbot (GET com query params); suporta sessão ou userId+conversationId.
     */
    @GET
    @Path("/chatbot")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chatbotLegacy(
            @QueryParam("session") String session,
            @QueryParam("userId") String userId,
            @QueryParam("conversationId") String conversationId,
            @QueryParam("prompt") @NotBlank String prompt) {
        // Formato legado para compatibilidade (GET com query params)
        if (userId != null && conversationId != null) {
            // Requer autenticação JWT para novo formato
            Log.info(
                    "Chatbot GET (legacy) User: "
                            + userId
                            + ", Conversation: "
                            + conversationId);

            // Sincronizar usuário do JWT token
            return syncUserFromRequest()
                    .onItem()
                    .transformToMulti(
                            syncedUser -> {
                                String syncedUserId = syncedUser.getId();
                                String syncedUserName = syncedUser.getUsername();
                                String syncedEmail = syncedUser.getEmail();
                                return conversationServicePort
                                        .userHasAccess(syncedUserId, conversationId)
                                        .onItem()
                                        .transformToMulti(
                                                hasAccess -> {
                                                    if (!hasAccess) {
                                                        Log.warn(
                                                                "Access denied for user "
                                                                        + syncedUserId
                                                                        + " to conversation "
                                                                        + conversationId);
                                                        return Multi.createFrom()
                                                                .failure(
                                                                        new SecurityException(
                                                                                "Acesso negado"));
                                                    }
                                                    return Multi.createFrom()
                                                            .publisher(
                                                                    chatbotUseCase
                                                                            .executeWithPhone(
                                                                                    syncedUserId,
                                                                                    conversationId,
                                                                                    prompt,
                                                                                    null,
                                                                                    syncedUserName,
                                                                                    syncedEmail));
                                                })
                                        .onFailure()
                                        .recoverWithMulti(
                                                e -> {
                                                    String errorMessage =
                                                            e instanceof SecurityException
                                                                    ? "Erro: Acesso negado à conversa"
                                                                    : "Erro: "
                                                                            + (e.getMessage()
                                                                                            != null
                                                                                    ? e.getMessage()
                                                                                    : "Erro desconhecido");
                                                    return Multi.createFrom()
                                                            .item(
                                                                    "data: "
                                                                            + errorMessage
                                                                            + "\n\n");
                                                });
                            })
                    .onFailure()
                    .invoke(e -> Log.error("Error processing chatbot request", e))
                    .onFailure()
                    .recoverWithMulti(
                            e -> {
                                String errorMessage =
                                        e.getMessage() != null
                                                ? e.getMessage()
                                                : "Erro desconhecido";
                                return Multi.createFrom()
                                        .item("data: Erro: " + errorMessage + "\n\n");
                            });
        } else if (session != null) {
            // Formato antigo para compatibilidade (sem autenticação JWT)
            Log.info("Chatbot GET (legacy) Session: " + session);
            return Multi.createFrom().publisher(chatbotUseCase.execute(session,
                prompt))
                .onFailure().recoverWithMulti(e -> {
                    Log.error("Error processing chatbot with session", e);
                    String errorMessage = e.getMessage() != null ?
                        e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + errorMessage
                        + "\n\n");
                });
        } else {
            return Multi.createFrom()
                    .item(
                            "data: Erro: Deve fornecer session ou "
                                    + "userId+conversationId\n\n");
        }
    }

    /**
     * Single-turn RAG question answering endpoint — no conversation memory.
     * Streams the AI response as Server-Sent Events.
     */
    @GET
    @Path("/ask")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> askModel(
            @QueryParam("session") @NotBlank String session,
            @QueryParam("prompt") @NotBlank String prompt) {
        Log.info("Ask Model Session: " + session);
        return Multi.createFrom().publisher(askQuestionUseCase.execute(session,
            prompt));
    }

    /**
     * Retrieves semantically relevant course content for a natural language query.
     * Used by MCP tools to provide course-aware context to LLMs.
     *
     * @param session    session/course identifier
     * @param prompt     natural language question
     * @param maxResults maximum number of context chunks to return (default: 5)
     * @return JSON with query, contexts, and relevance score
     */
    @GET
    @Path("/context")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<ContextResponse> getContext(
            @QueryParam("session") @NotBlank String session,
            @QueryParam("prompt") @NotBlank String prompt,
            @QueryParam("maxResults") @DefaultValue("5") int maxResults) {
        Log.info("Context retrieval Session: " + session + ", prompt: " +
            prompt);
        RagQuery query = new RagQuery(prompt, Math.min(Math.max(maxResults, 1),
            20), 0.5);
        return embeddingRepository.searchChunks(query)
                .collect().first()
                .map(ragResponse -> new ContextResponse(
                        ragResponse.getQuery(),
                        ragResponse.getContexts(),
                        ragResponse.getScore()))
                .onItem()
                .ifNull()
                .continueWith(() -> new ContextResponse(prompt, List.of(), 0.0));
    }

    @GET
    @Path("/memory")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<ConversationMemory> getMemory(
        @QueryParam("session") String session,
        @QueryParam("userId") String userId,
        @QueryParam("conversationId") String conversationId) {
        if (userId != null && conversationId != null) {
            Log.info("Memory User: " + userId + ", Conversation: " +
                conversationId);
            
            // Sincronizar usuário do JWT token
            return syncUserFromRequest()
                .onItem().transformToUni(syncedUser -> {
                    String userHash = syncedUser.getOrionUserHash();
                    return memoryPort.getConversationMemory(userHash,
                        conversationId);
                });
        } else if (session != null) {
            Log.info("Memory Session: " + session);
            return memoryPort.getConversationMemory(session);
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    /**
     * Exports request logs to CSV format for analysis and auditing.
     *
     * @return CSV content with all logged requests
     */
    @GET
    @Path("/logs/export")
    @Produces("text/csv")
    @WithSession
    @RolesAllowed("user")
    public Uni<Response> exportLogsToCsv() {
        return requestLogPort
                .exportToCsv()
                .map(
                        csv ->
                                Response.ok(csv)
                                        .header(
                                                "Content-Disposition",
                                                "attachment; filename=\"request_logs.csv\"")
                                        .build());
    }
    
    // ========== Endpoints de Usuário ==========
    
    /**
     * Creates a new user account from the provided username and e-mail.
     */
    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> createUser(@Valid UserRequest request) {
        Log.info("Creating user: " + request.username);
        return userServicePort.createUser(request.username, request.email);
    }
    
    @GET
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> getUser(@PathParam("userId") String userId) {
        Log.info("Getting user: " + userId);
        return userServicePort.getUserById(userId);
    }
    
    /**
     * Returns all user accounts in the system.
     */
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<User>> listUsers() {
        Log.info("Listing users");
        return userServicePort.listUsers();
    }
    
    // ========== Endpoints de Conversa ==========

    /**
     * Cria uma conversa para o utilizador identificado no path (JWT obrigatório).
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
        
        // Sincronizar usuário automaticamente do JWT token
        return syncUserFromRequest()
                .onItem()
                .transformToUni(
                        syncedUser ->
                                conversationServicePort.createConversation(
                                        syncedUser.getOrionUserHash(), request.title));
    }
    
    @GET
    @Path("/users/{userId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<List<Conversation>> getUserConversations(
            @PathParam("userId") String userId) {
        Log.info("Getting conversations for user: " + userId);
        
        // Sincronizar usuário automaticamente do JWT token
        return syncUserFromRequest()
                .onItem()
                .transformToUni(
                        syncedUser ->
                                conversationServicePort.getUserConversations(
                                        syncedUser.getOrionUserHash()));
    }
    
    @GET
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> getConversation(
            @PathParam("conversationId") String conversationId) {
        Log.info("Getting conversation: " + conversationId);
        return conversationServicePort.getConversation(conversationId);
    }

    /**
     * Remove uma conversa após validar o utilizador a partir do JWT.
     */
    @DELETE
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Response> deleteConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("userId") @NotBlank String userId) {
        Log.info("Deleting conversation " + conversationId + " by user " +
            userId);
        
        // Sincronizar usuário do JWT token
        return syncUserFromRequest()
            .onItem().transformToUni(syncedUser -> {
                // Usar o ID real do usuário sincronizado para deletar
                return conversationServicePort.deleteConversation(conversationId,
                    syncedUser.getId())
                    .replaceWith(Response.ok().build());
            });
    }
    
}
