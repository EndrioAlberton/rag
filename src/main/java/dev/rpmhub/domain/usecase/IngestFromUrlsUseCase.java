/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.usecase;

import dev.langchain4j.data.document.Document;
import dev.rpmhub.domain.port.EmbeddingRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use case for ingesting documents from a list of URLs (scrape → HTML → Markdown → chunk → embed).
 */
@ApplicationScoped
public class IngestFromUrlsUseCase {

    private final EmbeddingRepository embeddingRepository;
    private final dev.rpmhub.infrastructure.service.WebScraperService webScraperService;

    @Inject
    public IngestFromUrlsUseCase(EmbeddingRepository embeddingRepository,
                                 dev.rpmhub.infrastructure.service.WebScraperService webScraperService) {
        this.embeddingRepository = embeddingRepository;
        this.webScraperService = webScraperService;
    }

    /**
     * Scrapes each URL, converts HTML to Markdown, and ingests the resulting documents into the embedding store.
     * Failed URLs are logged and skipped; the rest are ingested.
     *
     * @param urls list of URLs to scrape and ingest
     * @return result summary: total URLs, ingested count, failed URLs
     */
    public IngestFromUrlsResult execute(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            Log.info("📭 Lista de URLs vazia, nada a ingerir.");
            return new IngestFromUrlsResult(0, 0, List.of());
        }
        // Before starting a new scrape cycle, clean the markdown directory (if enabled)
        webScraperService.clearMarkdownOutputDirIfEnabled();
        List<Document> documents = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String url : urls) {
            Optional<Document> doc = webScraperService.scrapeToDocument(url.trim());
            if (doc.isPresent()) {
                documents.add(doc.get());
            } else {
                failed.add(url);
            }
        }
        if (!documents.isEmpty()) {
            embeddingRepository.ingestDocuments(documents);
        }
        Log.info("✅ Ingestion from URLs: " + documents.size() + " document(s) ingested, " + failed.size() + " failure(s).");
        return new IngestFromUrlsResult(urls.size(), documents.size(), failed);
    }

    public static final class IngestFromUrlsResult {
        public final int totalUrls;
        public final int ingestedCount;
        public final List<String> failedUrls;

        public IngestFromUrlsResult(int totalUrls, int ingestedCount, List<String> failedUrls) {
            this.totalUrls = totalUrls;
            this.ingestedCount = ingestedCount;
            this.failedUrls = failedUrls != null ? List.copyOf(failedUrls) : List.of();
        }
    }
}
