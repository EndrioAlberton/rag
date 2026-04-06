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

import dev.orion.rag.domain.port.in.IngestDocumentsPort;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

/**
 * Runs local-directory document ingestion at startup using the configured rag.location.
 */
@ApplicationScoped
public class IngestDocumentsStartupObserver {

    /** Port used to trigger the document-ingestion use case. */
    private final IngestDocumentsPort ingestDocumentsPort;

    /** File-system path to the directory containing the base documents to ingest. */
    @ConfigProperty(name = "rag.location")
    Path documentsPath;

    /**
     * Constructs the observer with the required ingestion port.
     *
     * @param ingestDocumentsPort port that orchestrates document ingestion
     */
    @Inject
    public IngestDocumentsStartupObserver(IngestDocumentsPort
        ingestDocumentsPort) {
        this.ingestDocumentsPort = ingestDocumentsPort;
    }

    /**
     * Triggers document ingestion from the configured directory when the application starts.
     *
     * @param event CDI startup event (unused, only signals application readiness)
     */
    void onStartup(@Observes StartupEvent event) {
        try {
            ingestDocumentsPort.execute(documentsPath.toString());
        } catch (Exception e) {
            Log.error("Error ingesting documents at startup", e);
        }
    }
}
