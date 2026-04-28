package dev.orion.rag.application.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

/**
 * Redirects unknown frontend routes to index.html so Vue Router handles them (SPA fallback).
 */
@Provider
public class SpaFallbackFilter implements ContainerRequestFilter {

    @Context
    UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = uriInfo.getPath();

        // Only intercept non-API, non-asset GET requests
        if (!ctx.getMethod().equals("GET")) return;
        if (path.startsWith("ai/") || path.startsWith("q/") || path.startsWith("webhook/")
                || path.startsWith("mcp/") || path.startsWith("assets/")
                || path.contains(".")) {
            return;
        }

        // SPA routes — forward to index.html
        if (path.startsWith("chat") || path.startsWith("conversations")
                || path.startsWith("dashboard") || path.startsWith("settings")
                || path.startsWith("login") || path.startsWith("register")) {
            ctx.abortWith(Response
                    .temporaryRedirect(uriInfo.getBaseUri().resolve("index.html"))
                    .build());
        }
    }
}
