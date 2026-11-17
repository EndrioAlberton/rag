package dev.orion.rag.domain.model;

import java.time.LocalDateTime;

/** A source (file name or URL) already ingested into the vector store. */
public record IngestedSourceInfo(String source, LocalDateTime ingestedAt) {
}
