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

package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.DocumentData;

import java.util.Optional;

/**
 * Port for web scraping operations.
 * Driven port (out) — the domain defines the contract, infrastructure implements it.
 */
public interface WebScraperPort {

    /**
     * Scrapes a URL and returns the document content.
     *
     * @param url the URL to scrape
     * @return Optional containing the document data, or empty if scraping failed
     */
    Optional<DocumentData> scrapeToDocument(String url);

    /**
     * Clears the markdown output directory if saving is enabled.
     */
    void clearMarkdownOutputDirIfEnabled();
}
