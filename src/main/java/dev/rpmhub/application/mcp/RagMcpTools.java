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
import dev.rpmhub.domain.port.RequestLogService;
import dev.rpmhub.domain.usecase.AskQuestionUseCase;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
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

    @Inject
    RequestLogService requestLogService;

    @Tool(description = "Retrieves semantically relevant course content for a natural language query. Use this to provide course-aware context to the LLM.")
    public Uni<String> retrieveCourseContext(
            @ToolArg(description = "Natural language question or topic to search for") String query,
            @ToolArg(description = "Session/course identifier (optional, default: default)") String session,
            @ToolArg(description = "Maximum number of context chunks 1-20 (optional, default: 5)") Integer maxResults) {
        String sessionId = session != null && !session.isBlank() ? session : "default";
        int limit = maxResults != null ? Math.min(Math.max(maxResults, 1), 20) : 5;
        RagQuery ragQuery = new RagQuery(query, limit, 0.5);
        Instant messageTimestamp = Instant.now();
        long ragStart = System.currentTimeMillis();

        Log.info("MCP retrieve_course_context: session=" + sessionId + ", query=" + query);

        return embeddingRepository.searchChunks(ragQuery)
                .collect().first()
                .onItem().transformToUni(ragResponse -> {
                    long ragLatencyMs = System.currentTimeMillis() - ragStart;
                    String result;
                    if (ragResponse == null || ragResponse.getContexts().isEmpty()) {
                        result = "Query: " + query + "\nNo relevant context found.";
                    } else {
                        List<String> lines = new java.util.ArrayList<>();
                        lines.add("Query: " + ragResponse.getQuery());
                        lines.add("Relevance score: " + ragResponse.getScore());
                        lines.add("");
                        lines.add("Contexts:");
                        for (int i = 0; i < ragResponse.getContexts().size(); i++) {
                            lines.add("[" + (i + 1) + "] " + ragResponse.getContexts().get(i));
                        }
                        result = String.join("\n", lines);
                    }
                    String ragResult = result;
                    return requestLogService.log(null, sessionId, null, null, query,
                            messageTimestamp, ragResult, ragLatencyMs, null, 0L, null)
                            .replaceWith(ragResult);
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("MCP retrieve_course_context failed", e);
                    return "Error retrieving context: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                });
    }

}
