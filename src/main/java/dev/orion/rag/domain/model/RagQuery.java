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

/**
 * Domain representation of a RAG query — pure Java, no framework annotations.
 */
public class RagQuery {

    /** Natural-language question to be converted into an embedding and searched. */
    private final String query;
    /** Maximum number of embedding chunks to retrieve from the vector store. */
    private final int maxResults;
    /** Minimum cosine-similarity score required for a chunk to be included. */
    private final double minScore;

    /**
     * Creates a RagQuery with the given search parameters.
     *
     * @param query      natural-language question text
     * @param maxResults maximum number of chunks to return
     * @param minScore   minimum similarity score threshold (0.0 to 1.0)
     */
    public RagQuery(String query, int maxResults, double minScore) {
        this.query = query;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public String getQuery() {
        return query;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public double getMinScore() {
        return minScore;
    }
}
