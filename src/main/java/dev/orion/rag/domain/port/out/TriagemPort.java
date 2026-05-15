package dev.orion.rag.domain.port.out;

import dev.orion.rag.domain.model.TriagemResult;

import java.util.concurrent.CompletionStage;

public interface TriagemPort {
    CompletionStage<TriagemResult> classify(String userMessage, String history);
}
