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
import dev.orion.rag.domain.model.RagQuery;
import dev.orion.rag.domain.model.RagResponse;
import dev.orion.rag.domain.port.out.AIPort;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.RequestLogPort;
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
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskQuestionUseCaseTest {

    @Mock
    EmbeddingRepository embeddingRepository;

    @Mock
    AIPort aiPort;

    @Mock
    RequestLogPort requestLogPort;

    @Captor
    ArgumentCaptor<RagQuery> ragQueryCaptor;

    @Captor
    ArgumentCaptor<AIRequest> aiRequestCaptor;

    AskQuestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AskQuestionUseCase(embeddingRepository, aiPort, requestLogPort);
    }

    @Test
    void execute_streamsTokens_andLogsWithFirstRagChunk() throws Exception {
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of("ctx-a", "ctx-b"), 0.8)));
        Flow.Publisher<String> stream = FlowTestSupport.emitTokens("hel", "lo");
        when(aiPort.generateResponse(any())).thenReturn(stream);
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong(),
                any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<String> out = FlowTestSupport.collectAll(
                useCase.execute("sess-1", "question?", "+5511", "Ada", "ada@x.test")).get();

        assertEquals(List.of("hel", "lo"), out);

        verify(embeddingRepository).searchChunks(ragQueryCaptor.capture());
        assertEquals("question?", ragQueryCaptor.getValue().getQuery());

        verify(aiPort).generateResponse(aiRequestCaptor.capture());
        AIRequest req = aiRequestCaptor.getValue();
        assertEquals("sess-1", req.getSession());
        assertEquals("question?", req.getPrompt());
        assertEquals("ctx-a", req.getContext());

        verify(requestLogPort).log(
                eq("+5511"),
                eq("sess-1"),
                eq("Ada"),
                eq("ada@x.test"),
                eq("question?"),
                any(),
                eq("ctx-a"),
                anyLong(),
                eq("hello"),
                anyLong(),
                isNull());
    }

    @Test
    void execute_usesEmptyContextWhenNoChunks() throws Exception {
        when(embeddingRepository.searchChunks(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagResponse("q", List.of(), 0.0)));
        when(aiPort.generateResponse(any())).thenReturn(FlowTestSupport.emitTokens("ok"));
        when(requestLogPort.log(any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong(),
                any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        FlowTestSupport.collectAll(useCase.execute("s", "p")).get();

        verify(aiPort).generateResponse(aiRequestCaptor.capture());
        assertEquals("", aiRequestCaptor.getValue().getContext());
    }
}
