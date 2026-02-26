/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application;

import dev.rpmhub.domain.usecase.IngestFromUrlsUseCase;
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

    private final IngestFromUrlsUseCase ingestFromUrlsUseCase;

    @ConfigProperty(name = "rag.scrape.urls", defaultValue = "")
    Optional<String> scrapeUrlsConfig;

    @Inject
    public ScrapeUrlsStartupObserver(IngestFromUrlsUseCase ingestFromUrlsUseCase) {
        this.ingestFromUrlsUseCase = ingestFromUrlsUseCase;
    }

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
            Log.info("🚀 Starting scrape ingestion of " + urls.size() + " URL(s) (rag.scrape.urls).");
            IngestFromUrlsUseCase.IngestFromUrlsResult result = ingestFromUrlsUseCase.execute(urls);
            Log.info("✅ Startup scrape ingestion: " + result.ingestedCount + " document(s) ingested, " + result.failedUrls.size() + " failure(s).");
        } catch (Exception e) {
            Log.error("❌ Error in startup scrape ingestion", e);
        }
    }
}
