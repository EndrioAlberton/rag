/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.model.RagResponse;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.infrastructure.service.PDFExtractorService;
import dev.rpmhub.infrastructure.util.BlockingToReactive;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the EmbeddingRepository interface using LangChain4j.
 */
@ApplicationScoped
public class EmbeddingRepositoryImpl implements EmbeddingRepository {

        private final EmbeddingStore<TextSegment> embeddingStore;
        private final EmbeddingModel embeddingModel;
        private final PDFExtractorService pdfService;

        @Inject
        public EmbeddingRepositoryImpl(
                        EmbeddingStore<TextSegment> embeddingStore,
                        EmbeddingModel embeddingModel,
                        PDFExtractorService pdfExtractorService) {
                this.embeddingStore = embeddingStore;
                this.embeddingModel = embeddingModel;
                this.pdfService = pdfExtractorService;
        }

        /**
         * Searches for relevant chunks based on the provided RagQuery.
         *
         * @param query the RagQuery containing the search parameters
         * @return a Multi emitting RagResponse objects with the search results
         */
        @Override
        public Multi<RagResponse> searchChunks(RagQuery query) {
                // Use wrapper to execute blocking operations (embedding + search) on executor thread
                // and emit result back on EventLoop thread
                return BlockingToReactive.wrap(() -> {
                        // These operations are blocking:
                        // 1. embeddingModel.embed() - ML model processing
                        // 2. embeddingStore.search() - Database query to Chroma
                        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                                        .queryEmbedding(embeddingModel.embed(query.getQuery())
                                                        .content())
                                        .minScore(query.getMinScore())
                                        .maxResults(query.getMaxResults())
                                        .build();

                        var matches = embeddingStore.search(searchRequest).matches();
                        var contexts = matches.stream()
                                        .map(match -> match.embedded().text())
                                        .toList();

                        double score = matches.isEmpty() ? 0.0 : matches.get(0).score();

                        return new RagResponse(query.getQuery(), contexts, score);
                })
                .toMulti();
        }

        /**
         * Ingests documents from the specified directory into the embedding store.
         *
         * @param directoryPath the path to the directory containing the documents
         */
        @Override
        public void ingestDocuments(String directoryPath) {
                try {
                        List<Document> documents = new ArrayList<>();
                        Path dirPath = Path.of(directoryPath);

                        try (var files = Files.walk(dirPath)) {
                                files.filter(Files::isRegularFile)
                                        .forEach(file -> {
                                                if (pdfService.isPdfFile(file)) {
                                                        String extractedText = pdfService
                                                                        .extractText(file);
                                                        if (!extractedText.isEmpty()) {
                                                                Document pdfDocument = Document
                                                                                .from(extractedText);
                                                                documents.add(pdfDocument);
                                                                Log.info("PDF processed: "
                                                                                + file.getFileName());
                                                        }
                                                } else {
                                                        Document fileDoc = FileSystemDocumentLoader
                                                                        .loadDocument(file);
                                                        documents.add(fileDoc);
                                                        Log.info("File processed: " + file.getFileName());
                                                }
                                        });
                        }

                        Log.info("📂 Total filesystem documents loaded from directory '" + directoryPath
                                        + "': " + documents.size());

                        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                                        .embeddingStore(embeddingStore)
                                        .embeddingModel(embeddingModel)
                                        .documentSplitter(recursive(800, 300, new HuggingFaceTokenCountEstimator()))
                                        .build();

                        ingestor.ingest(documents);
                        Log.info("Ingestion completed successfully!");

                } catch (IOException e) {
                        Log.error("Error processing directory: " + directoryPath, e);
                }
        }

        /**
         * Ingests the given documents into the embedding store (same chunking and embedding pipeline as directory ingest).
         *
         * @param documents the list of documents to ingest
         */
        @Override
        public void ingestDocuments(List<Document> documents) {
                if (documents == null || documents.isEmpty()) {
                        Log.info("📭 No documents to ingest.");
                        return;
                }
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                                .embeddingStore(embeddingStore)
                                .embeddingModel(embeddingModel)
                                .documentSplitter(recursive(800, 300, new HuggingFaceTokenCountEstimator()))
                                .build();
                ingestor.ingest(documents);
                Log.info("✅ Ingestion of " + documents.size() + " document(s) completed successfully!");
        }
}
