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
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    /** Tracks per-source fingerprints to skip re-embedding unchanged content. */
    private final IngestedSourceTracker sourceTracker;

    /**
     * Creates an EmbeddingRepositoryImpl with all required collaborators.
     *
     * @param embeddingStore      LangChain4j store holding vectorised text segments
     * @param embeddingModel      model used to embed queries before similarity search
     * @param pdfExtractorService service used to extract text from PDFs before ingestion
     * @param sourceTracker       tracker of per-source ingestion fingerprints
     */
    @Inject
    public EmbeddingRepositoryImpl(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            PDFExtractorService pdfExtractorService,
            IngestedSourceTracker sourceTracker) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.pdfService = pdfExtractorService;
        this.sourceTracker = sourceTracker;
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
                    .map(match -> {
                        String source = null;
                        try {
                            // Some stores may not persist metadata; best effort only.
                            source = match.embedded().metadata() != null
                                    ? match.embedded().metadata().getString("source")
                                    : null;
                        } catch (Exception ignored) {
                        }
                        String prefix = (source == null || source.isBlank())
                                ? ""
                                : ("[Fonte: " + source + "]\n");
                        return prefix + match.embedded().text();
                    })
                    .toList();

            double score = matches.isEmpty() ? 0.0 : matches.get(0).score();

            return new RagResponse(query.getQuery(), contexts, score);
        }).subscribeAsCompletionStage();
    }

    @Override
    public void ingestDocuments(String directoryPath) {
        try {
            Path dirPath = Path.of(directoryPath);
            List<Path> files;
            try (var walk = Files.walk(dirPath)) {
                files = walk.filter(Files::isRegularFile).toList();
            }

            List<FingerprintedDocument> candidates = new ArrayList<>();
            for (Path file : files) {
                String source = file.getFileName().toString();
                String hash;
                try {
                    hash = sha256(Files.readAllBytes(file));
                } catch (IOException e) {
                    Log.error("Error reading file for fingerprint: " + file, e);
                    continue;
                }
                if (sourceTracker.findHash(source).filter(hash::equals).isPresent()) {
                    Log.info("⏭️  Unchanged, skipping: " + source);
                    continue;
                }

                Document document;
                if (pdfService.isPdfFile(file)) {
                    String extractedText = pdfService.extractText(file);
                    if (extractedText.isEmpty()) {
                        continue;
                    }
                    document = Document.from(extractedText, Metadata.from("source", source));
                    Log.info("PDF processed: " + source);
                } else {
                    document = FileSystemDocumentLoader.loadDocument(file);
                    try {
                        document.metadata().put("source", source);
                    } catch (Exception ignored) {
                    }
                    Log.info("File processed: " + source);
                }
                candidates.add(new FingerprintedDocument(source, hash, document));
            }

            Log.info("📂 " + files.size() + " filesystem document(s) scanned in '"
                    + directoryPath + "', " + candidates.size() + " new/changed");
            ingestDeduplicated(candidates);

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
        List<FingerprintedDocument> candidates = documents.stream()
                .map(dd -> new FingerprintedDocument(
                        dd.getSource(),
                        sha256(dd.getText().getBytes(StandardCharsets.UTF_8)),
                        Document.from(dd.getText(), Metadata.from("source", dd.getSource()))))
                .toList();
        ingestDeduplicated(candidates);
    }

    /**
     * Filters out sources whose fingerprint has not changed since the last ingestion,
     * replaces the stale chunks of changed sources, embeds only what is new or changed,
     * and records the new fingerprints. Skips the OpenAI embedding call entirely when
     * nothing changed.
     *
     * @param candidates sources considered for ingestion, with their content fingerprint
     */
    private void ingestDeduplicated(List<FingerprintedDocument> candidates) {
        if (candidates.isEmpty()) {
            Log.info("✅ Nothing to ingest — no embedding API calls made.");
            return;
        }

        List<FingerprintedDocument> changed = new ArrayList<>();
        for (FingerprintedDocument candidate : candidates) {
            Optional<String> existingHash = sourceTracker.findHash(candidate.source());
            if (existingHash.isPresent()) {
                if (existingHash.get().equals(candidate.sha256())) {
                    continue;
                }
                Log.info("🔄 Source changed, replacing existing chunks: " + candidate.source());
                embeddingStore.removeAll(
                        MetadataFilterBuilder.metadataKey("source").isEqualTo(candidate.source()));
            }
            changed.add(candidate);
        }

        int unchanged = candidates.size() - changed.size();
        if (changed.isEmpty()) {
            Log.info("✅ All " + candidates.size()
                    + " source(s) already up to date — no embedding API calls made.");
            return;
        }

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(800, 300,
                        new HuggingFaceTokenCountEstimator()))
                .build();
        ingestor.ingest(changed.stream().map(FingerprintedDocument::document).toList());
        changed.forEach(c -> sourceTracker.recordIngestion(c.source(), c.sha256()));

        Log.info("✅ Ingested " + changed.size() + " new/changed source(s); "
                + unchanged + " unchanged source(s) skipped.");
    }

    /**
     * Computes the SHA-256 hex digest of the given bytes, used as a fingerprint to
     * detect whether a source's content changed since the last ingestion.
     *
     * @param bytes content to hash
     * @return lowercase hex-encoded SHA-256 digest
     */
    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** A candidate document paired with its source identifier and content fingerprint. */
    private record FingerprintedDocument(String source, String sha256, Document document) {
    }
}
