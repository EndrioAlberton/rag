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

package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.DocumentData;
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.model.RagResponse;
import io.smallrye.mutiny.Multi;

import java.util.List;

public interface EmbeddingRepository {

    /**
     * Searches for relevant chunks based on the provided RAG query.
     *
     * @param query the RAG query containing the search parameters
     * @return a Multi emitting the found RAG responses
     */
    Multi<RagResponse> searchChunks(RagQuery query);

    /**
     * Ingests documents from the specified directory.
     *
     * @param directoryPath the path to the directory containing the documents
     */
    void ingestDocuments(String directoryPath);

    /**
     * Ingests the given documents into the embedding store (chunking + embedding + persist).
     *
     * @param documents the list of documents to ingest
     */
    void ingestDocuments(List<DocumentData> documents);
}
