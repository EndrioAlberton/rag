/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest;

import dev.rpmhub.domain.usecase.IngestFromUrlsUseCase;
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

    private final IngestFromUrlsUseCase ingestFromUrlsUseCase;

    @Inject
    public IngestController(IngestFromUrlsUseCase ingestFromUrlsUseCase) {
        this.ingestFromUrlsUseCase = ingestFromUrlsUseCase;
    }

    @POST
    @Path("/urls")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Response ingestFromUrls(@Valid IngestUrlsRequest request) {
        IngestFromUrlsUseCase.IngestFromUrlsResult result = ingestFromUrlsUseCase.execute(request.urls);
        return Response.accepted().entity(result).build();
    }

    public static class IngestUrlsRequest {
        @NotNull
        public java.util.List<String> urls;
    }
}
