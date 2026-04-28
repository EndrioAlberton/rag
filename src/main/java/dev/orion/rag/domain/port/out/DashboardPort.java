package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.DashboardMetrics;

import java.util.concurrent.CompletionStage;

public interface DashboardPort {
    CompletionStage<DashboardMetrics> metrics();
}

