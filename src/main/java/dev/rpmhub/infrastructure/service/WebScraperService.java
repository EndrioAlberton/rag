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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

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

    @ConfigProperty(name = "rag.scrape.save-markdown", defaultValue = "false")
    boolean saveMarkdownToDisk;

    @ConfigProperty(name = "rag.scrape.save-markdown.dir", defaultValue = "target/scraped-markdown")
    String markdownOutputDir;

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

            String body = response.body();
            String markdown = toMarkdownFromResponse(url, body);
            if (markdown.isBlank()) {
                Log.warn("⚠️ Conteúdo vazio ou conversão falhou para " + url);
                return Optional.empty();
            }

            persistMarkdownIfEnabled(url, markdown);

            Metadata metadata = Metadata.from("source", url);
            Document document = Document.from(markdown, metadata);
            return Optional.of(document);
        } catch (Exception e) {
            Log.warn("❌ Error scraping " + url, e);
            return Optional.empty();
        }
    }

    /**
     * Produces markdown from the HTTP response body. If the URL ends with .md and the body
     * is not HTML, treats the body as raw markdown (strips nav menu + normalizes line breaks).
     * Otherwise converts HTML to markdown and strips the menu.
     */
    private String toMarkdownFromResponse(String url, String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        boolean urlEndsWithMd = url != null && url.trim().toLowerCase().endsWith(".md");
        boolean bodyLooksLikeHtml = body.trim().startsWith("<");
        if (urlEndsWithMd && !bodyLooksLikeHtml) {
            String md = htmlToMarkdownService.stripLeadingNavigationMenu(body);
            return htmlToMarkdownService.normalizeMarkdownLineBreaks(md);
        }
        String md = htmlToMarkdownService.toMarkdown(body);
        return htmlToMarkdownService.normalizeMarkdownLineBreaks(md);
    }

    private void persistMarkdownIfEnabled(String url, String markdown) {
        if (!saveMarkdownToDisk) {
            return;
        }
        try {
            Path dir = Path.of(markdownOutputDir);
            Files.createDirectories(dir);

            String safeFileName = toSafeFileName(url) + ".md";
            Path file = dir.resolve(safeFileName);

            Files.writeString(file, markdown, StandardCharsets.UTF_8);
            Log.debug("💾 Saved scraped markdown for " + url + " to " + file);
        } catch (Exception e) {
            Log.warn("⚠️ Failed to persist markdown for " + url, e);
        }
    }

    /**
     * Clears all existing markdown files from the output directory when saving is enabled.
     * Used at the start of a scrape ingestion run to avoid mixing old and new files.
     */
    public void clearMarkdownOutputDirIfEnabled() {
        if (!saveMarkdownToDisk) {
            return;
        }
        try {
            Path dir = Path.of(markdownOutputDir);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        Log.warn("⚠️ Failed to delete old markdown file " + path, e);
                    }
                });
            }
            Log.debug("🧹 Cleared existing markdown files from directory " + dir);
        } catch (Exception e) {
            Log.warn("⚠️ Failed to clear markdown output directory " + markdownOutputDir, e);
        }
    }

    private String toSafeFileName(String url) {
        String sanitized = url
                .replaceFirst("^https?://", "")
                .replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        if (sanitized.isBlank()) {
            sanitized = "document";
        }
        return sanitized + "_" + System.currentTimeMillis();
    }
}
