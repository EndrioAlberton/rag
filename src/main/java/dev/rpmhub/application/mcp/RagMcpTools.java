/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.mcp;

import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.domain.usecase.AskQuestionUseCase;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP tools for course-aware Q&A via RAG.
 * Exposes retrieve_course_context and ask_course_question to LLM platforms (Claude, Cursor).
 */
@ApplicationScoped
public class RagMcpTools {

    @Inject
    EmbeddingRepository embeddingRepository;

    @Inject
    AskQuestionUseCase askQuestionUseCase;

    @Tool(description = "Retrieves semantically relevant course content for a natural language query. Use this to provide course-aware context to the LLM.")
    public Uni<String> retrieveCourseContext(
            @ToolArg(description = "Natural language question or topic to search for") String query,
            @ToolArg(description = "Session/course identifier (optional, default: default)") String session,
            @ToolArg(description = "Maximum number of context chunks 1-20 (optional, default: 5)") Integer maxResults) {
        String sessionId = session != null && !session.isBlank() ? session : "default";
        int limit = maxResults != null ? Math.min(Math.max(maxResults, 1), 20) : 5;
        RagQuery ragQuery = new RagQuery(query, limit, 0.5);

        Log.info("MCP retrieve_course_context: session=" + sessionId + ", query=" + query);

        return embeddingRepository.searchChunks(ragQuery)
                .collect().first()
                .onItem().transform(ragResponse -> {
                    if (ragResponse == null || ragResponse.getContexts().isEmpty()) {
                        return "Query: " + query + "\nNo relevant context found.";
                    }
                    List<String> lines = new java.util.ArrayList<>();
                    lines.add("Query: " + ragResponse.getQuery());
                    lines.add("Relevance score: " + ragResponse.getScore());
                    lines.add("");
                    lines.add("Contexts:");
                    for (int i = 0; i < ragResponse.getContexts().size(); i++) {
                        lines.add("[" + (i + 1) + "] " + ragResponse.getContexts().get(i));
                    }
                    return String.join("\n", lines);
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("MCP retrieve_course_context failed", e);
                    return "Error retrieving context: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                });
    }

    @Tool(description = "Asks a question about the course content. The RAG backend retrieves relevant context and generates an answer. Returns the full response.")
    public Uni<String> askCourseQuestion(
            @ToolArg(description = "Question to ask about the course") String query,
            @ToolArg(description = "Session/course identifier") String session) {
        String sessionId = session != null && !session.isBlank() ? session : "default";

        Log.info("MCP ask_course_question: session=" + sessionId + ", query=" + query);

        return askQuestionUseCase.execute(sessionId, query)
                .collect().asList()
                .onItem().transform(chunks -> {
                    String response = chunks.stream().collect(Collectors.joining());
                    return response != null && !response.isBlank() ? response : "(No response)";
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("MCP ask_course_question failed", e);
                    return "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                });
    }
}
