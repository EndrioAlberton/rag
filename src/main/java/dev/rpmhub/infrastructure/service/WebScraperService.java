/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches HTML from URLs and converts to LangChain4j Document (Markdown + source metadata).
 */
@ApplicationScoped
public class WebScraperService {

    private final HtmlToMarkdownService htmlToMarkdownService;
    private final HttpClient httpClient;

    @ConfigProperty(name = "rag.scrape.timeout-seconds", defaultValue = "30")
    int timeoutSeconds;

    @ConfigProperty(name = "rag.scrape.user-agent", defaultValue = "RAG-Scraper/1.0")
    String userAgent;

    @Inject
    public WebScraperService(HtmlToMarkdownService htmlToMarkdownService) {
        this.htmlToMarkdownService = htmlToMarkdownService;
        int timeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches the URL, converts HTML to Markdown, and returns a Document with source metadata.
     *
     * @param url the URL to scrape
     * @return Optional containing the Document, or empty if fetch/conversion failed
     */
    public Optional<Document> scrapeToDocument(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            int timeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                Log.warn("⚠️ Scrape falhou para " + url + ": HTTP " + response.statusCode());
                return Optional.empty();
            }

            String html = response.body();
            String markdown = htmlToMarkdownService.toMarkdown(html);
            if (markdown.isBlank()) {
                Log.warn("⚠️ HTML→Markdown conversion returned empty for " + url);
                return Optional.empty();
            }

            Metadata metadata = Metadata.from("source", url);
            Document document = Document.from(markdown, metadata);
            return Optional.of(document);
        } catch (Exception e) {
            Log.warn("❌ Error scraping " + url, e);
            return Optional.empty();
        }
    }
}
