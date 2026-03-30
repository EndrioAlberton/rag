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

import dev.orion.rag.domain.port.in.IngestFromUrlsPort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoint for triggering ingestion from URLs (scrape → Markdown → chunks → vector store).
 */
@Path("/ai/ingest")
public class IngestController {

    /** Port used to trigger URL scraping and ingestion. */
    private final IngestFromUrlsPort ingestFromUrlsUseCase;

    /**
     * Constructs the controller with the required URL-ingestion port.
     *
     * @param ingestFromUrlsUseCase port that orchestrates URL scraping and ingestion
     */
    @Inject
    public IngestController(IngestFromUrlsPort ingestFromUrlsUseCase) {
        this.ingestFromUrlsUseCase = ingestFromUrlsUseCase;
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
    @RolesAllowed("user")
    public Response ingestFromUrls(@Valid IngestUrlsRequest request) {
        IngestFromUrlsPort.IngestFromUrlsResult result =
            ingestFromUrlsUseCase.execute(request.urls);
        return Response.accepted().entity(result).build();
    }

    /**
     * Request body for the URL ingestion endpoint.
     */
    public static class IngestUrlsRequest {
        /** Non-null list of URLs to scrape and ingest into the vector store. */
        @NotNull
        public java.util.List<String> urls;
    }
}
