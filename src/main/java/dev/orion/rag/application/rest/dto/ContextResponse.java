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

package dev.orion.rag.application.rest.dto;

import java.util.List;

/**
 * Response for context retrieval (MCP / course-aware Q&A).
 */
public class ContextResponse {

    /** Original query text used to search the embedding store. */
    public final String query;
    /** Ordered list of retrieved context passages, best match first. */
    public final List<String> contexts;
    /** Highest similarity score among the retrieved passages. */
    public final double score;

    /**
     * Constructs a ContextResponse with the query, contexts and top score.
     *
     * @param query    original query text
     * @param contexts retrieved context passages
     * @param score    highest similarity score
     */
    public ContextResponse(String query, List<String> contexts, double score) {
        this.query = query;
        this.contexts = contexts;
        this.score = score;
    }
}
