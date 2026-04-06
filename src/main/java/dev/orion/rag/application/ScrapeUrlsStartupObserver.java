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

package dev.orion.rag.application;

import dev.orion.rag.domain.port.in.IngestFromUrlsPort;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs scrape-based ingestion at startup when {@code rag.scrape.urls} is configured.
 */
@ApplicationScoped
public class ScrapeUrlsStartupObserver {

    /** Port used to trigger URL-based ingestion at startup. */
    private final IngestFromUrlsPort ingestFromUrlsUseCase;

    /** Comma-separated list of URLs to scrape and ingest on startup; empty means disabled. */
    @ConfigProperty(name = "rag.scrape.urls", defaultValue = "")
    Optional<String> scrapeUrlsConfig;

    /**
     * Constructs the observer with the required URL-ingestion port.
     *
     * @param ingestFromUrlsUseCase port that orchestrates URL scraping and ingestion
     */
    @Inject
    public ScrapeUrlsStartupObserver(IngestFromUrlsPort ingestFromUrlsUseCase) {
        this.ingestFromUrlsUseCase = ingestFromUrlsUseCase;
    }

    /**
     * Reads the configured URL list and ingests all pages when the application starts.
     * Exits early and silently when no URLs are configured.
     *
     * @param event CDI startup event (unused, only signals application readiness)
     */
    void onStartup(@Observes StartupEvent event) {
        if (scrapeUrlsConfig.isEmpty() || scrapeUrlsConfig.get().isBlank()) {
            return;
        }
        List<String> urls = Arrays.stream(scrapeUrlsConfig.get().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (urls.isEmpty()) {
            return;
        }
        try {
            Log.info(
                    "🚀 Starting scrape ingestion of "
                            + urls.size()
                            + " URL(s) (rag.scrape.urls).");
            IngestFromUrlsPort.IngestFromUrlsResult result =
                    ingestFromUrlsUseCase.execute(urls);
            Log.info(
                    "✅ Startup scrape ingestion: "
                            + result.ingestedCount()
                            + " document(s) ingested, "
                            + result.failedUrls().size()
                            + " failure(s).");
        } catch (Exception e) {
            Log.error("❌ Error in startup scrape ingestion", e);
        }
    }
}
