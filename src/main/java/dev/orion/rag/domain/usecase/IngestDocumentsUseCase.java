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

import dev.orion.rag.domain.port.in.IngestDocumentsPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import io.quarkus.logging.Log;

/**
 * Use case for ingesting documents into the embedding repository.
 * Pure Java — instantiated by the Composition Root.
 */
public class IngestDocumentsUseCase implements IngestDocumentsPort {

    /** Repository used to store embedded document chunks in the vector store. */
    private final EmbeddingRepository embeddingRepository;

    /**
     * Creates an IngestDocumentsUseCase with the given embedding repository.
     *
     * @param embeddingRepository repository for document ingestion and storage
     */
    public IngestDocumentsUseCase(EmbeddingRepository embeddingRepository) {
        this.embeddingRepository = embeddingRepository;
    }

    @Override
    public void execute(String documentsPath) {
        try {
            embeddingRepository.ingestDocuments(documentsPath);
            Log.info("📂 Base documents from '" + documentsPath
                    + "' ingested successfully");
        } catch (Exception e) {
            Log.error("Error ingesting documents", e);
        }
    }
}
