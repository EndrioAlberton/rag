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

package dev.orion.rag.domain.model;

/**
 * Domain representation of a document to be ingested into the embedding store.
 * Replaces framework-specific types (e.g. LangChain4j Document) in domain ports.
 */
public class DocumentData {

    /** Full textual content of the document to be embedded. */
    private final String text;
    /** Origin of the document, such as a file path or a URL. */
    private final String source;

    /**
     * Creates a DocumentData with the given text content and source reference.
     *
     * @param text   full textual content of the document
     * @param source origin of the document (file path, URL, etc.)
     */
    public DocumentData(String text, String source) {
        this.text = text;
        this.source = source;
    }

    public String getText() {
        return text;
    }

    public String getSource() {
        return source;
    }
}
