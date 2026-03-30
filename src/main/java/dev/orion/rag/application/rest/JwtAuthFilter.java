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

import dev.orion.rag.domain.port.out.AuthService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter to extract JWT token and synchronize user from Orion Users.
 * Only processes requests that have Authorization header.
 */
@Provider
public class JwtAuthFilter implements ContainerRequestFilter {
    
    /** Authentication service used to extract user data from JWT tokens. */
    @Inject
    AuthService authService;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader =
            requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        
        // Only process if Authorization header is present
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            
            // Store JWT token in request context for later use by endpoints
            // The synchronization will be done lazily in the endpoints that need it
            // This avoids reactive context conflicts
            requestContext.setProperty("jwt.token", jwtToken);
            
            Log.debug("JWT token stored in request context");
        }
    }
}

