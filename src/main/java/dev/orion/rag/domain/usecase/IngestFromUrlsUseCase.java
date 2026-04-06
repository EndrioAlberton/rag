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

package dev.orion.rag.domain.usecase;

import dev.orion.rag.domain.model.DocumentData;
import dev.orion.rag.domain.port.in.IngestFromUrlsPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.WebScraperPort;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use case for ingesting documents from a list of URLs (scrape to Markdown to chunks to embed).
 * Pure Java — instantiated by the Composition Root.
 */
public class IngestFromUrlsUseCase implements IngestFromUrlsPort {

    /** Repository used to store embedded document chunks in the vector store. */
    private final EmbeddingRepository embeddingRepository;
    /** Port that scrapes remote URLs and converts HTML content to Markdown documents. */
    private final WebScraperPort webScraperPort;

    /**
     * Creates an IngestFromUrlsUseCase with the given collaborators.
     *
     * @param embeddingRepository repository for document ingestion and storage
     * @param webScraperPort      port for scraping and converting web pages
     */
    public IngestFromUrlsUseCase(EmbeddingRepository embeddingRepository,
                                 WebScraperPort webScraperPort) {
        this.embeddingRepository = embeddingRepository;
        this.webScraperPort = webScraperPort;
    }

    /**
     * Scrapes each URL, converts HTML to Markdown, and ingests the resulting documents into the embedding store.
     * Failed URLs are logged and skipped; the rest are ingested.
     *
     * @param urls list of URLs to scrape and ingest
     * @return result summary: total URLs, ingested count, failed URLs
     */
    @Override
    public IngestFromUrlsResult execute(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            Log.info("📭 Lista de URLs vazia, nada a ingerir.");
            return new IngestFromUrlsResult(0, 0, List.of());
        }
        webScraperPort.clearMarkdownOutputDirIfEnabled();
        List<DocumentData> documents = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String url : urls) {
            Optional<DocumentData> doc =
                webScraperPort.scrapeToDocument(url.trim());
            if (doc.isPresent()) {
                documents.add(doc.get());
            } else {
                failed.add(url);
            }
        }
        if (!documents.isEmpty()) {
            embeddingRepository.ingestDocuments(documents);
        }
        Log.info("✅ Ingestion from URLs: " + documents.size()
                + " document(s) ingested, " + failed.size() + " failure(s).");
        return new IngestFromUrlsResult(urls.size(), documents.size(), failed);
    }
}
