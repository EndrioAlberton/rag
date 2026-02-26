/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.port;

import dev.langchain4j.data.document.Document;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.model.RagResponse;
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
    void ingestDocuments(List<Document> documents);
}
