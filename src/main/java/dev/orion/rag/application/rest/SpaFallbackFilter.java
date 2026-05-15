package dev.orion.rag.application.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * Catch-all JAX-RS resource that redirects unknown SPA routes to index.html.
 * More specific resources (e.g. /ai/*) take priority over this catch-all.
 */
@Path("{path: .*}")
@PermitAll
public class SpaFallbackFilter {

    @GET
    public Response spa(@PathParam("path") String path) {
        // Let API, health, static assets and files through
        if (path.startsWith("ai/") || path.startsWith("q/")
                || path.startsWith("mcp/") || path.startsWith("webhook/")
                || path.contains(".")) {
            return Response.status(404).build();
        }
        return Response.temporaryRedirect(URI.create("/")).build();
    }
}
