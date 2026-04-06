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

package dev.orion.rag.infrastructure.repository;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.orion.rag.domain.model.DocumentData;
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.model.RagResponse;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.infrastructure.BlockingToReactive;
import dev.orion.rag.infrastructure.service.PDFExtractorService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

/**
 * Implementation of the EmbeddingRepository interface using LangChain4j.
 * Converts the blocking embedding search to {@link CompletionStage} at the boundary.
 */
@ApplicationScoped
public class EmbeddingRepositoryImpl implements EmbeddingRepository {

    /** LangChain4j store that holds vectorised text segments. */
    private final EmbeddingStore<TextSegment> embeddingStore;
    /** Model used to vectorise queries before similarity search. */
    private final EmbeddingModel embeddingModel;
    /** Service used to extract text from PDF documents before ingestion. */
    private final PDFExtractorService pdfService;

    /**
     * Creates an EmbeddingRepositoryImpl with all required collaborators.
     *
     * @param embeddingStore      LangChain4j store holding vectorised text segments
     * @param embeddingModel      model used to embed queries before similarity search
     * @param pdfExtractorService service used to extract text from PDFs before ingestion
     */
    @Inject
    public EmbeddingRepositoryImpl(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            PDFExtractorService pdfExtractorService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.pdfService = pdfExtractorService;
    }

    @Override
    public CompletionStage<RagResponse> searchChunks(RagQuery query) {
        return BlockingToReactive.wrap(() -> {
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(query.getQuery()).content())
                    .minScore(query.getMinScore())
                    .maxResults(query.getMaxResults())
                    .build();

            var matches = embeddingStore.search(searchRequest).matches();
            var contexts = matches.stream()
                    .map(match -> match.embedded().text())
                    .toList();

            double score = matches.isEmpty() ? 0.0 : matches.get(0).score();

            return new RagResponse(query.getQuery(), contexts, score);
        }).subscribeAsCompletionStage();
    }

    @Override
    public void ingestDocuments(String directoryPath) {
        try {
            List<Document> documents = new ArrayList<>();
            Path dirPath = Path.of(directoryPath);

            try (var files = Files.walk(dirPath)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> {
                            if (pdfService.isPdfFile(file)) {
                                String extractedText = pdfService.extractText(file);
                                if (!extractedText.isEmpty()) {
                                    Document pdfDocument = Document.from(extractedText);
                                    documents.add(pdfDocument);
                                    Log.info("PDF processed: " + file.getFileName());
                                }
                            } else {
                                Document fileDoc = FileSystemDocumentLoader.loadDocument(file);
                                documents.add(fileDoc);
                                Log.info("File processed: " + file.getFileName());
                            }
                        });
            }

            Log.info("📂 Total filesystem documents loaded from directory '"
                    + directoryPath + "': " + documents.size());

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(recursive(800, 300,
                            new HuggingFaceTokenCountEstimator()))
                    .build();

            ingestor.ingest(documents);
            Log.info("Ingestion completed successfully!");

        } catch (IOException e) {
            Log.error("Error processing directory: " + directoryPath, e);
        }
    }

    @Override
    public void ingestDocuments(List<DocumentData> documents) {
        if (documents == null || documents.isEmpty()) {
            Log.info("📭 No documents to ingest.");
            return;
        }
        List<Document> langChainDocs = documents.stream()
                .map(dd -> Document.from(dd.getText(),
                        Metadata.from("source", dd.getSource())))
                .toList();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(800, 300,
                        new HuggingFaceTokenCountEstimator()))
                .build();
        ingestor.ingest(langChainDocs);
        Log.info("✅ Ingestion of " + documents.size()
                + " document(s) completed successfully!");
    }
}
