package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.DashboardMetrics;
import dev.orion.rag.domain.model.InteractionSummary;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DashboardPort {
    CompletionStage<DashboardMetrics> metrics();

    /** Returns interactions with the given urgency (BAIXA / MEDIA / ALTA). */
    CompletionStage<List<InteractionSummary>> interactionsByUrgency(String urgency);

    /** Returns interactions with LIKE (true) or DISLIKE (false) feedback. */
    CompletionStage<List<InteractionSummary>> interactionsByFeedback(boolean liked);
}

