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

package dev.orion.rag.application;

import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.RequestLogPort;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tools for course-aware Q&A via RAG.
 * Exposes retrieve_course_context to LLM platforms (Claude, Cursor).
 * Converts domain {@link java.util.concurrent.CompletionStage} to Mutiny {@link Uni}
 * at the application boundary (MCP framework expects {@link Uni}).
 */
@ApplicationScoped
public class RagMcpTools {

    /** Port for embedding-based retrieval of course context. */
    @Inject
    EmbeddingRepository embeddingRepository;

    /** Vert.x instance — used to dispatch Hibernate Reactive log writes to the EventLoop (HR000068). */
    @Inject
    Vertx vertx;

    /** Port for persisting request/response audit logs. */
    @Inject
    RequestLogPort requestLogPort;

    @Tool(
            description =
                    "Retrieves semantically relevant course content for a natural "
                            + "language query. Use this to provide course-aware context "
                            + "to the LLM.")
    public Uni<String> retrieveCourseContext(
            @ToolArg(description = "Natural language question or topic to search for")
                    String query,
            @ToolArg(description = "Session/course identifier (optional, default: default)")
                    String session,
            @ToolArg(description = "Maximum number of context chunks 1-20 (optional, default: 5)")
                    Integer maxResults) {
        String sessionId = session != null && !session.isBlank() ? session : "default";
        int limit = maxResults != null ? Math.min(Math.max(maxResults, 1), 20) : 5;
        RagQuery ragQuery = new RagQuery(query, limit, 0.5);
        Instant messageTimestamp = Instant.now();
        long ragStart = System.currentTimeMillis();

        Log.info("MCP retrieve_course_context: session=" + sessionId + ", query=" + query);

        return Uni.createFrom().completionStage(() -> embeddingRepository.searchChunks(ragQuery))
                .onItem().transformToUni(ragResponse -> {
                    long ragLatencyMs = System.currentTimeMillis() - ragStart;
                    String result;
                    if (ragResponse == null || ragResponse.getContexts().isEmpty()) {
                        result = "Query: " + query + "\nNo relevant context found.";
                    } else {
                        List<String> lines = new ArrayList<>();
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
                    return Uni.createFrom().item(ragResult)
                            .emitOn(vertx.nettyEventLoopGroup())
                            .onItem().transformToUni(r ->
                                    Uni.createFrom().completionStage(() -> requestLogPort.log(
                                            null, sessionId, null, null, query, messageTimestamp,
                                            r, null, ragLatencyMs,
                                            false, null,
                                            null, 0L, null, null))
                                            .replaceWith(r));
                })
                .onFailure().recoverWithItem(e -> {
                    Log.error("MCP retrieve_course_context failed", e);
                    return "Error retrieving context: "
                            + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                });
    }
}
