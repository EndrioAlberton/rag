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

package dev.orion.rag.application.rest;

import dev.orion.rag.application.rest.dto.IngestUrlsRequest;
import dev.orion.rag.domain.model.DocumentData;
import dev.orion.rag.domain.port.in.IngestFromUrlsPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.infrastructure.service.PDFExtractorService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * REST endpoint for triggering ingestion from URLs (scrape → Markdown → chunks → vector store).
 */
@Path("/ai/ingest")
public class IngestController {

    /** Port used to trigger URL scraping and ingestion. */
    private final IngestFromUrlsPort ingestFromUrlsUseCase;
    private final EmbeddingRepository embeddingRepository;
    private final PDFExtractorService pdfService;

    /**
     * Constructs the controller with the required URL-ingestion port.
     *
     * @param ingestFromUrlsUseCase port that orchestrates URL scraping and ingestion
     */
    @Inject
    public IngestController(IngestFromUrlsPort ingestFromUrlsUseCase,
                            EmbeddingRepository embeddingRepository,
                            PDFExtractorService pdfService) {
        this.ingestFromUrlsUseCase = ingestFromUrlsUseCase;
        this.embeddingRepository = embeddingRepository;
        this.pdfService = pdfService;
    }

    /**
     * Accepts a JSON list of URLs and triggers their ingestion into the vector store.
     *
     * @param request request body containing the list of URLs to ingest
     * @return HTTP 202 Accepted with an ingestion result summary
     */
    @POST
    @Path("/urls")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Response ingestFromUrls(@Valid IngestUrlsRequest request) {
        IngestFromUrlsPort.IngestFromUrlsResult result =
            ingestFromUrlsUseCase.execute(request.urls);
        return Response.accepted().entity(result).build();
    }

    /**
     * Receives PDF, Markdown or text files and ingests them into the vector store. The file name
     * is the source id, so re-uploading a file with the same name replaces its previous chunks.
     *
     * @param files uploaded files (.pdf, .md or .txt)
     * @return HTTP 200 with the ingested and rejected file names
     */
    @POST
    @Path("/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Response ingestFiles(@RestForm("files") List<FileUpload> files) {
        List<DocumentData> documents = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (FileUpload f : files == null ? List.<FileUpload>of() : files) {
            String name = f.fileName();
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            try {
                String text;
                if (lower.endsWith(".pdf")) {
                    text = pdfService.extractText(f.uploadedFile());
                } else if (lower.endsWith(".md") || lower.endsWith(".txt")) {
                    text = Files.readString(f.uploadedFile(), StandardCharsets.UTF_8);
                } else {
                    rejected.add(name);
                    continue;
                }
                if (text.isBlank()) {
                    rejected.add(name);
                } else {
                    documents.add(new DocumentData(text, name));
                }
            } catch (IOException e) {
                rejected.add(name);
            }
        }
        if (!documents.isEmpty()) {
            embeddingRepository.ingestDocuments(documents);
        }
        return Response.ok(java.util.Map.of(
                "ingested", documents.stream().map(DocumentData::getSource).toList(),
                "rejected", rejected)).build();
    }

}
