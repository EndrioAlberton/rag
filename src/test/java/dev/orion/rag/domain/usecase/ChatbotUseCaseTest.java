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

package dev.orion.rag.domain.usecase;

import dev.orion.rag.domain.model.AIRequest;
import dev.orion.rag.domain.model.ChatMessage;
import dev.orion.rag.domain.model.RagResponse;
import dev.orion.rag.domain.model.TriagemResult;
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.MemoryPort;
import dev.orion.rag.domain.port.out.RequestLogPort;
import dev.orion.rag.domain.port.out.TriagemPort;
import dev.orion.rag.domain.testsupport.FlowTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotUseCaseTest {

    @Mock
    EmbeddingRepository embeddingRepository;

    @Mock
    AIPort aiPort;

    @Mock
    MemoryPort memoryPort;

    @Mock
    RequestLogPort requestLogPort;

    @Mock
    TriagemPort triagemPort;

    @Captor
    ArgumentCaptor<AIRequest> aiRequestCaptor;

    @Captor
    ArgumentCaptor<ChatMessage> messageCaptor;

    ChatbotUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChatbotUseCase(embeddingRepository, aiPort, memoryPort, requestLogPort, triagemPort);
        // Default: triagem returns AUTO_RESPONDER BAIXA. Lenient so the PEDIR_INFO test can override it.
        lenient().when(triagemPort.classify(any(), any())).thenReturn(CompletableFuture.completedFuture(
            new TriagemResult(TriagemResult.Decisao.AUTO_RESPONDER, TriagemResult.Urgencia.BAIXA, null)));
    }

    @Test
    void authenticatedFlow_savesUserAndAssistant_andLogs() throws Exception {
        when(memoryPort.saveMessage(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of("rag-text"), 0.9)));
        when(memoryPort.getHistory(eq("user-1"), eq("conv-1")))
                .thenReturn(CompletableFuture.completedFuture("hist-line"));
        when(aiPort.generateContextualResponse(any()))
                .thenReturn(FlowTestSupport.emitTokens("A", "I"));
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyBoolean(), any(), any(), anyLong(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<String> tokens = FlowTestSupport.collectAll(
                useCase.execute("user-1", "conv-1", "hi", "N", "n@e")).get();

        // The LLM tokens come first; the pipeline appends the sources block on completion (RF03).
        assertEquals(List.of("A", "I"), tokens.subList(0, 2));
        assertTrue(tokens.get(tokens.size() - 1).contains("Fontes consultadas"));

        verify(memoryPort, atLeast(1)).saveMessage(messageCaptor.capture());
        List<ChatMessage> saved = messageCaptor.getAllValues();
        assertEquals(ChatMessage.MessageType.USER, saved.get(0).getType());
        assertEquals("conv-1", saved.get(0).getConversationId());
        assertEquals(ChatMessage.MessageType.ASSISTANT, saved.get(saved.size() - 1).getType());
        assertTrue(saved.get(saved.size() - 1).getContent().startsWith("AI"));

        verify(aiPort).generateContextualResponse(aiRequestCaptor.capture());
        AIRequest req = aiRequestCaptor.getValue();
        assertEquals("conv-1", req.getSession());
        assertEquals("rag-text", req.getContext());
        assertEquals("hist-line", req.getHistory());

        verify(requestLogPort).log(
                eq("user-1"),
                eq("N"),
                eq("n@e"),
                eq("hi"),
                any(),
                eq("rag-text"),
                eq(0.9),
                anyLong(),
                eq(false),
                isNull(),
                startsWith("AI"),
                anyLong(),
                eq("conv-1"),
                any());
    }

    @Test
    void userConversationFlow_usesConversationIdAsSessionInAiRequest() throws Exception {
        when(memoryPort.saveMessage(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of("c"), 1.0)));
        when(memoryPort.getHistory(eq("user-1"), eq("conv-1")))
                .thenReturn(CompletableFuture.completedFuture(""));
        when(aiPort.generateContextualResponse(any()))
                .thenReturn(FlowTestSupport.emitTokens("x"));
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyBoolean(), any(), any(), anyLong(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        FlowTestSupport.collectAll(
                useCase.execute("user-1", "conv-1", "q")).get();

        verify(aiPort).generateContextualResponse(aiRequestCaptor.capture());
        assertEquals("conv-1", aiRequestCaptor.getValue().getSession());

        verify(requestLogPort).log(
                eq("user-1"),
                isNull(),
                isNull(),
                eq("q"),
                any(),
                eq("c"),
                eq(1.0),
                anyLong(),
                eq(false),
                isNull(),
                startsWith("x"),
                anyLong(),
                eq("conv-1"),
                any());
    }

    @Test
    void pedirInfo_persistsClarification_andSkipsRag() throws Exception {
        when(triagemPort.classify(any(), any())).thenReturn(CompletableFuture.completedFuture(
                new TriagemResult(TriagemResult.Decisao.PEDIR_INFO,
                        TriagemResult.Urgencia.BAIXA, "curso")));
        when(memoryPort.saveMessage(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(memoryPort.getHistory(eq("user-1"), eq("conv-1")))
                .thenReturn(CompletableFuture.completedFuture(""));

        List<String> tokens = FlowTestSupport.collectAll(
                useCase.execute("user-1", "conv-1", "e ai?", null, null)).get();

        assertEquals(1, tokens.size());
        assertTrue(tokens.get(0).contains("preciso de mais detalhes"));
        assertTrue(tokens.get(0).contains("curso"));

        // The clarification must land in the history, otherwise triagem repeats the request forever.
        verify(memoryPort, atLeast(2)).saveMessage(messageCaptor.capture());
        ChatMessage clarification = messageCaptor.getAllValues().get(1);
        assertEquals(ChatMessage.MessageType.ASSISTANT, clarification.getType());
        assertEquals("conv-1", clarification.getConversationId());

        // RAG and the LLM are skipped entirely on this branch.
        verifyNoInteractions(embeddingRepository, aiPort, requestLogPort);
    }

    @Test
    void emptyRagContext_answersWithHumanHandoff_andLogsIt() throws Exception {
        when(memoryPort.saveMessage(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of(), 0.0)));
        when(memoryPort.getHistory(eq("user-1"), eq("conv-1")))
                .thenReturn(CompletableFuture.completedFuture(""));
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyBoolean(), any(), any(), anyLong(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<String> tokens = FlowTestSupport.collectAll(
                useCase.execute("user-1", "conv-1", "algo fora da base", null, null)).get();

        assertEquals(1, tokens.size());
        assertTrue(tokens.get(0).contains("Não encontrei informação suficiente"));
        assertTrue(tokens.get(0).contains("comunicacao@poa.ifrs.edu.br"));

        // The LLM is never called when there is no grounding context.
        verifyNoInteractions(aiPort);

        verify(requestLogPort).log(
                eq("user-1"),
                isNull(),
                isNull(),
                eq("algo fora da base"),
                any(),
                eq(""),
                eq(0.0),
                anyLong(),
                eq(true),
                eq("no_context"),
                any(),
                anyLong(),
                eq("conv-1"),
                any());
    }
}
