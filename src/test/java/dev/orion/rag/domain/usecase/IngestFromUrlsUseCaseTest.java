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

import dev.orion.rag.domain.model.DocumentData;
import dev.orion.rag.domain.port.in.IngestFromUrlsPort.IngestFromUrlsResult;
import dev.orion.rag.domain.port.out.EmbeddingRepository;
import dev.orion.rag.domain.port.out.WebScraperPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestFromUrlsUseCaseTest {

    @Mock
    EmbeddingRepository embeddingRepository;

    @Mock
    WebScraperPort webScraperPort;

    @Captor
    ArgumentCaptor<List<DocumentData>> documentsCaptor;

    IngestFromUrlsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IngestFromUrlsUseCase(embeddingRepository, webScraperPort);
    }

    @Test
    void emptyList_returnsZeroTotalsAndDoesNotIngest() {
        IngestFromUrlsResult r = useCase.execute(List.of());
        assertEquals(0, r.totalUrls());
        assertEquals(0, r.ingestedCount());
        assertTrue(r.failedUrls().isEmpty());
        verify(embeddingRepository, never()).ingestDocuments(anyList());
    }

    @Test
    void nullList_treatedAsEmpty() {
        IngestFromUrlsResult r = useCase.execute(null);
        assertEquals(0, r.totalUrls());
        verify(embeddingRepository, never()).ingestDocuments(anyList());
    }

    @Test
    void successfulScrapes_trimsUrl_ingestsDocuments_andClearsOutputDir() {
        DocumentData doc = new DocumentData("md", "https://a.example");
        when(webScraperPort.scrapeToDocument("https://a.example")).thenReturn(Optional.of(doc));

        IngestFromUrlsResult r = useCase.execute(List.of("  https://a.example  "));

        verify(webScraperPort).clearMarkdownOutputDirIfEnabled();
        verify(embeddingRepository).ingestDocuments(documentsCaptor.capture());
        assertEquals(1, documentsCaptor.getValue().size());
        assertEquals(doc, documentsCaptor.getValue().get(0));
        assertEquals(1, r.totalUrls());
        assertEquals(1, r.ingestedCount());
        assertTrue(r.failedUrls().isEmpty());
    }

    @Test
    void failedScrapes_collectsFailedUrls_andSkipsIngestWhenAllFail() {
        when(webScraperPort.scrapeToDocument("https://bad")).thenReturn(Optional.empty());

        IngestFromUrlsResult r = useCase.execute(List.of("https://bad"));

        verify(embeddingRepository, never()).ingestDocuments(anyList());
        assertEquals(1, r.totalUrls());
        assertEquals(0, r.ingestedCount());
        assertEquals(List.of("https://bad"), r.failedUrls());
    }
}
