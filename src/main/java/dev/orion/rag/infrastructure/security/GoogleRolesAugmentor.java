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

package dev.orion.rag.infrastructure.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Locale;

/**
 * Assigns application roles to Google-authenticated users, since Google ID tokens carry no
 * {@code groups} claim. Every verified account (optionally restricted to one e-mail domain)
 * gets {@code user}; e-mails listed in {@code rag.admin-emails} also get {@code admin}.
 */
@ApplicationScoped
public class GoogleRolesAugmentor implements SecurityIdentityAugmentor {

    /** Comma-separated e-mails that receive the admin role. */
    @ConfigProperty(name = "rag.admin-emails", defaultValue = "")
    List<String> adminEmails;

    /** Optional e-mail domain restriction; empty accepts any Google account. */
    @ConfigProperty(name = "rag.allowed-email-domain", defaultValue = "")
    String allowedDomain;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        String email = jwt.getClaim("email");
        Boolean verified = jwt.getClaim("email_verified");
        if (email == null || !Boolean.TRUE.equals(verified)) {
            return Uni.createFrom().item(identity);
        }
        email = email.toLowerCase(Locale.ROOT);
        if (!allowedDomain.isBlank() && !email.endsWith("@" + allowedDomain.toLowerCase(Locale.ROOT))) {
            return Uni.createFrom().item(identity);
        }
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity).addRole("user");
        if (adminEmails.stream().anyMatch(a -> a.trim().equalsIgnoreCase(email))) {
            builder.addRole("admin");
        }
        return Uni.createFrom().item(builder.build());
    }
}
