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

package dev.orion.rag.domain.port.in;

import java.util.List;

/**
 * Driving port (in) for ingesting documents from URLs.
 */
public interface IngestFromUrlsPort {

    /**
     * Scrapes each URL, converts the HTML content to Markdown, and ingests the result into the embedding store.
     *
     * @param urls list of URLs to scrape and ingest
     * @return summary of the operation including total, ingested and failed counts
     */
    IngestFromUrlsResult execute(List<String> urls);

    /**
     * Summary of a URL-based ingestion operation.
     *
     * @param totalUrls     total number of URLs submitted
     * @param ingestedCount number of URLs successfully scraped and ingested
     * @param failedUrls    list of URLs that could not be scraped or ingested
     */
    record IngestFromUrlsResult(int totalUrls, int ingestedCount, List<String>
        failedUrls) {
    }
}
