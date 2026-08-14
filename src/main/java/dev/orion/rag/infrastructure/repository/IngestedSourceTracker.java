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

package dev.orion.rag.infrastructure.repository;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Tracks the SHA-256 fingerprint of each ingested source (local file name or scraped
 * URL) in the {@code ingested_sources} table, so startup ingestion can skip sources
 * that have not changed since the last run.
 *
 * <p>Deliberately implemented with plain blocking JDBC instead of Hibernate Reactive:
 * its only caller, {@code EmbeddingRepositoryImpl.ingestDocuments}, is itself a
 * synchronous method invoked once from a {@code StartupEvent} observer, not from the
 * reactive request path.
 */
@ApplicationScoped
public class IngestedSourceTracker {

    /** Blocking datasource, provided by {@code quarkus-jdbc-postgresql}. */
    private final DataSource dataSource;

    /**
     * Creates a tracker backed by the given blocking datasource.
     *
     * @param dataSource blocking JDBC datasource
     */
    @Inject
    public IngestedSourceTracker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Looks up the last recorded fingerprint for the given source.
     *
     * @param source source identifier (file name or URL)
     * @return the stored SHA-256 hex digest, or empty if never ingested or on error
     */
    public Optional<String> findHash(String source) {
        String sql = "SELECT sha256 FROM ingested_sources WHERE source = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, source);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            Log.error("Error reading ingested_sources fingerprint for " + source, e);
            // Fail open toward re-ingestion, never toward silently skipping a source.
            return Optional.empty();
        }
    }

    /**
     * Records (or updates) the fingerprint for a source that was just ingested.
     *
     * @param source source identifier (file name or URL)
     * @param sha256 SHA-256 hex digest of the source content
     */
    public void recordIngestion(String source, String sha256) {
        String sql = "INSERT INTO ingested_sources (source, sha256, ingested_at) VALUES (?, ?, now()) "
                + "ON CONFLICT (source) DO UPDATE SET sha256 = EXCLUDED.sha256, ingested_at = now()";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, source);
            stmt.setString(2, sha256);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Log.error("Error recording ingested_sources fingerprint for " + source, e);
        }
    }
}
