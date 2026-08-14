-- Tracks a SHA-256 fingerprint per ingested source (local file name or scraped URL) so
-- startup ingestion can skip documents that have not changed since the last run. Without
-- this, every restart re-embedded and re-inserted all base documents into the vector
-- store — which persists across restarts in a Docker volume — duplicating every chunk
-- and drowning fresh matches under repeats in similarity search, on top of the wasted
-- OpenAI embedding calls.
CREATE TABLE IF NOT EXISTS ingested_sources (
    source      VARCHAR(512) PRIMARY KEY,
    sha256      CHAR(64) NOT NULL,
    ingested_at TIMESTAMP NOT NULL DEFAULT now()
);
