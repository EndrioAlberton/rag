/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.model.ConversationMemory;
import dev.rpmhub.domain.model.User;
import dev.rpmhub.domain.port.AuthService;
import dev.rpmhub.domain.port.ConversationService;
import dev.rpmhub.domain.port.MemoryService;
import dev.rpmhub.domain.port.RequestLogService;
import dev.rpmhub.domain.port.UserService;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.domain.usecase.AskQuestionUseCase;
import dev.rpmhub.domain.usecase.ChatbotUseCase;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.List;

@Path("/ai")
public class RagController {

    private final ChatbotUseCase chatbotUseCase;
    private final AskQuestionUseCase askQuestionUseCase;
    private final EmbeddingRepository embeddingRepository;
    private final MemoryService memoryService;
    private final UserService userService;
    private final ConversationService conversationService;
    private final AuthService authService;
    private final RequestLogService requestLogService;

    @Context
    ContainerRequestContext requestContext;

    @Inject
    public RagController(ChatbotUseCase chatbotUseCase,
            AskQuestionUseCase askQuestionUseCase,
            EmbeddingRepository embeddingRepository,
            MemoryService memoryService,
            UserService userService,
            ConversationService conversationService,
            AuthService authService,
            RequestLogService requestLogService) {

        this.chatbotUseCase = chatbotUseCase;
        this.askQuestionUseCase = askQuestionUseCase;
        this.embeddingRepository = embeddingRepository;
        this.memoryService = memoryService;
        this.userService = userService;
        this.conversationService = conversationService;
        this.authService = authService;
        this.requestLogService = requestLogService;
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
            return Uni.createFrom().failure(new IllegalArgumentException("JWT token não encontrado no contexto da requisição"));
        }
        
        return authService.syncUserFromJwt(jwtToken)
            .onFailure().invoke(e -> Log.error("Failed to synchronize user from JWT token", e));
    }

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
                    return conversationService.userHasAccess(syncedUserId, request.conversationId)
                        .onItem().transformToMulti(hasAccess -> {
                            if (!hasAccess) {
                                Log.warn("Access denied for user " + syncedUserId + " to conversation " + request.conversationId);
                                return Multi.createFrom().failure(new SecurityException("Acesso negado"));
                            }
                            return chatbotUseCase.executeWithPhone(syncedUserId, request.conversationId,
                                    request.prompt, null, syncedUserName, syncedEmail);
                        })
                        .onFailure().recoverWithMulti(e -> {
                            // Tratar falhas convertendo em mensagem SSE válida
                            String errorMessage = e instanceof SecurityException 
                                ? "Erro: Acesso negado à conversa" 
                                : "Erro: " + (e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                            return Multi.createFrom().item("data: " + errorMessage + "\n\n");
                        });
                })
                .onFailure().invoke(e -> Log.error("Error processing chatbot request", e))
                .onFailure().recoverWithMulti(e -> {
                    // Tratar falhas na sincronização do usuário
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + errorMessage + "\n\n");
                });
        } else {
            // Token não presente - retornar erro
            Log.warn("JWT token not found in POST /chatbot request");
            return Multi.createFrom().item("data: Erro: Token de autenticação não encontrado\n\n");
        }
    }
    
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
            Log.info("Chatbot GET (legacy) User: " + userId + ", Conversation: " + conversationId);
            
            // Sincronizar usuário do JWT token
            return syncUserFromRequest()
                .onItem().transformToMulti(syncedUser -> {
                    String syncedUserId = syncedUser.getId();
                    String syncedUserName = syncedUser.getUsername();
                    String syncedEmail = syncedUser.getEmail();
                    // Verificar acesso antes de processar (usando o ID do usuário)
                    return conversationService.userHasAccess(syncedUserId, conversationId)
                        .onItem().transformToMulti(hasAccess -> {
                            if (!hasAccess) {
                                Log.warn("Access denied for user " + syncedUserId + " to conversation " + conversationId);
                                return Multi.createFrom().failure(new SecurityException("Acesso negado"));
                            }
                            return chatbotUseCase.executeWithPhone(syncedUserId, conversationId,
                                    prompt, null, syncedUserName, syncedEmail);
                        })
                        .onFailure().recoverWithMulti(e -> {
                            // Tratar falhas convertendo em mensagem SSE válida
                            String errorMessage = e instanceof SecurityException 
                                ? "Erro: Acesso negado à conversa" 
                                : "Erro: " + (e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                            return Multi.createFrom().item("data: " + errorMessage + "\n\n");
                        });
                })
                .onFailure().invoke(e -> Log.error("Error processing chatbot request", e))
                .onFailure().recoverWithMulti(e -> {
                    // Tratar falhas na sincronização do usuário
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + errorMessage + "\n\n");
                });
        } else if (session != null) {
            // Formato antigo para compatibilidade (sem autenticação JWT)
            Log.info("Chatbot GET (legacy) Session: " + session);
            return chatbotUseCase.execute(session, prompt)
                .onFailure().recoverWithMulti(e -> {
                    Log.error("Error processing chatbot with session", e);
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                    return Multi.createFrom().item("data: Erro: " + errorMessage + "\n\n");
                });
        } else {
            return Multi.createFrom().item("data: Erro: Deve fornecer session ou userId+conversationId\n\n");
        }
    }

    @GET
    @Path("/ask")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> askModel(
            @QueryParam("session") @NotBlank String session,
            @QueryParam("prompt") @NotBlank String prompt) {
        Log.info("Ask Model Session: " + session);
        return askQuestionUseCase.execute(session, prompt);
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
        Log.info("Context retrieval Session: " + session + ", prompt: " + prompt);
        RagQuery query = new RagQuery(prompt, Math.min(Math.max(maxResults, 1), 20), 0.5);
        return embeddingRepository.searchChunks(query)
                .collect().first()
                .map(ragResponse -> new ContextResponse(
                        ragResponse.getQuery(),
                        ragResponse.getContexts(),
                        ragResponse.getScore()))
                .onItem().ifNull().continueWith(() -> new ContextResponse(prompt, List.of(), 0.0));
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
            Log.info("Memory User: " + userId + ", Conversation: " + conversationId);
            
            // Sincronizar usuário do JWT token
            return syncUserFromRequest()
                .onItem().transformToUni(syncedUser -> {
                    String userHash = syncedUser.getOrionUserHash();
                    return memoryService.getConversationMemory(userHash, conversationId);
                });
        } else if (session != null) {
            Log.info("Memory Session: " + session);
            return memoryService.getConversationMemory(session);
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
        return requestLogService.exportToCsv()
                .map(csv -> Response.ok(csv)
                        .header("Content-Disposition", "attachment; filename=\"request_logs.csv\"")
                        .build());
    }
    
    // ========== Endpoints de Usuário ==========
    
    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> createUser(@Valid UserRequest request) {
        Log.info("Creating user: " + request.username);
        return userService.createUser(request.username, request.email);
    }
    
    @GET
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> getUser(@PathParam("userId") String userId) {
        Log.info("Getting user: " + userId);
        return userService.getUserById(userId);
    }
    
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<User>> listUsers() {
        Log.info("Listing users");
        return userService.listUsers();
    }
    
    // ========== Endpoints de Conversa ==========
    
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
            .onItem().transformToUni(syncedUser -> {
                // Usar o hash do usuário sincronizado para criar a conversa
                return conversationService.createConversation(syncedUser.getOrionUserHash(), request.title);
            });
    }
    
    @GET
    @Path("/users/{userId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<List<Conversation>> getUserConversations(@PathParam("userId") String userId) {
        Log.info("Getting conversations for user: " + userId);
        
        // Sincronizar usuário automaticamente do JWT token
        return syncUserFromRequest()
            .onItem().transformToUni(syncedUser -> {
                // Usar o hash do usuário sincronizado para buscar conversas
                return conversationService.getUserConversations(syncedUser.getOrionUserHash());
            });
    }
    
    @GET
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> getConversation(@PathParam("conversationId") String conversationId) {
        Log.info("Getting conversation: " + conversationId);
        return conversationService.getConversation(conversationId);
    }
    
    @DELETE
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Response> deleteConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("userId") @NotBlank String userId) {
        Log.info("Deleting conversation " + conversationId + " by user " + userId);
        
        // Sincronizar usuário do JWT token
        return syncUserFromRequest()
            .onItem().transformToUni(syncedUser -> {
                // Usar o ID real do usuário sincronizado para deletar
                return conversationService.deleteConversation(conversationId, syncedUser.getId())
                    .replaceWith(Response.ok().build());
            });
    }
    
    // ========== DTOs ==========
    
    public static class UserRequest {
        @NotBlank
        public String username;
        
        @NotBlank
        public String email;
    }
    
    public static class ConversationRequest {
        @NotBlank
        public String title;
    }
    
    public static class ChatbotRequest {
        @NotBlank
        public String conversationId;
        
        @NotBlank
        public String prompt;
    }

    /**
     * Response for context retrieval (MCP / course-aware Q&A).
     */
    public static class ContextResponse {
        public final String query;
        public final List<String> contexts;
        public final double score;

        public ContextResponse(String query, List<String> contexts, double score) {
            this.query = query;
            this.contexts = contexts;
            this.score = score;
        }
    }
}
