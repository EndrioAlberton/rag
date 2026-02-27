/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.usecase;

import dev.rpmhub.domain.port.EmbeddingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import java.nio.file.Path;

/**
 * Use case for ingesting documents into the embedding repository.
 */
@ApplicationScoped
public class IngestDocumentsUseCase {

    /**
     * Repository for managing embeddings.
     */
    private final EmbeddingRepository embeddingRepository;

    @Inject
    public IngestDocumentsUseCase(EmbeddingRepository embeddingRepository) {
        this.embeddingRepository = embeddingRepository;
    }

    public void execute(@Observes StartupEvent ev,
            @ConfigProperty(name = "rag.location") Path documents) {
        try {
            embeddingRepository.ingestDocuments(documents.toString());
            Log.info("📂 Base documents from rag.location '" + documents + "' ingested successfully");
        } catch (Exception e) {
            Log.error("Error ingesting documents", e);
        }
    }
}
