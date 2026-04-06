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

package dev.orion.rag.domain.model;

import java.util.List;

/**
 * Domain representation of a RAG response — pure Java, no framework annotations.
 */
public class RagResponse {

    /** The original query text that was used to retrieve the context passages. */
    private final String query;
    /** Ordered list of context passages retrieved from the embedding store. */
    private final List<String> contexts;
    /** Highest similarity score among the retrieved passages. */
    private final double score;

    /**
     * Creates a RagResponse with the query, retrieved contexts and top score.
     *
     * @param query    original query text
     * @param contexts list of retrieved context passages, best match first
     * @param score    highest similarity score among the results
     */
    public RagResponse(String query, List<String> contexts, double score) {
        this.query = query;
        this.contexts = contexts;
        this.score = score;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public double getScore() {
        return score;
    }

    /**
     * Returns the top-ranked context passage, or an empty string if no results were found.
     *
     * @return best-matching context passage, or {@code ""}
     */
    public String getFirstContext() {
        return contexts.isEmpty() ? "" : contexts.get(0);
    }
}
