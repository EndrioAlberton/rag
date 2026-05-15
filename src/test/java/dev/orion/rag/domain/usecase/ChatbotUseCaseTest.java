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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
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
        // Default: triagem returns AUTO_RESPONDER BAIXA
        when(triagemPort.classify(any(), any())).thenReturn(CompletableFuture.completedFuture(
            new TriagemResult(TriagemResult.Decisao.AUTO_RESPONDER, TriagemResult.Urgencia.BAIXA, null)));
    }

    @Test
    void sessionFlow_savesUserAndAssistant_andLogs() throws Exception {
        when(memoryPort.saveMessage(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of("rag-text"), 0.9)));
        when(memoryPort.getHistory(eq("sess-x")))
                .thenReturn(CompletableFuture.completedFuture("hist-line"));
        when(aiPort.generateContextualResponse(any()))
                .thenReturn(FlowTestSupport.emitTokens("A", "I"));
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyBoolean(), any(), any(), anyLong(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<String> tokens = FlowTestSupport.collectAll(
                useCase.executeWithPhone("sess-x", "hi", "+99", "N", "n@e")).get();

        assertEquals(List.of("A", "I"), tokens);

        verify(memoryPort, atLeast(1)).saveMessage(messageCaptor.capture());
        List<ChatMessage> saved = messageCaptor.getAllValues();
        assertEquals(ChatMessage.MessageType.USER, saved.get(0).getType());
        assertEquals("sess-x", saved.get(0).getSessionId());
        assertEquals(ChatMessage.MessageType.ASSISTANT, saved.get(saved.size() - 1).getType());
        assertEquals("AI", saved.get(saved.size() - 1).getContent());

        verify(aiPort).generateContextualResponse(aiRequestCaptor.capture());
        AIRequest req = aiRequestCaptor.getValue();
        assertEquals("sess-x", req.getSession());
        assertEquals("rag-text", req.getContext());
        assertEquals("hist-line", req.getHistory());

        verify(requestLogPort).log(
                eq("+99"),
                eq("sess-x"),
                eq("N"),
                eq("n@e"),
                eq("hi"),
                any(),
                eq("rag-text"),
                eq(0.9),
                anyLong(),
                eq(false),
                isNull(),
                eq("AI"),
                anyLong(),
                isNull(),
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
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyBoolean(), any(), any(), anyLong(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        FlowTestSupport.collectAll(
                useCase.executeWithPhone("user-1", "conv-1", "q", null, null, null)).get();

        verify(aiPort).generateContextualResponse(aiRequestCaptor.capture());
        assertEquals("conv-1", aiRequestCaptor.getValue().getSession());

        verify(requestLogPort).log(
                isNull(),
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
                eq("x"),
                anyLong(),
                eq("conv-1"),
                any());
    }
}
