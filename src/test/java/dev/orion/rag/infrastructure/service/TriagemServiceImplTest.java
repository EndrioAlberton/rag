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

package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.TriagemResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the deterministic {@code SOBRE_ASSISTENTE} short-circuit, added after gpt-4o-mini
 * proved unreliable at following that rule from the prompt alone — observed failure:
 * "para oq vc serve" was classified as {@code PEDIR_INFO}, with the model echoing the
 * example text from the {@code PEDIR_INFO} rule almost verbatim into {@code camposFaltantes}.
 */
@ExtendWith(MockitoExtension.class)
class TriagemServiceImplTest {

    @Mock
    TriagemServiceImpl.TriagemAI triagemAI;

    @ParameterizedTest
    @ValueSource(strings = {
            "para oq vc serve",
            "para que você serve",
            "o que vc faz",
            "quem é você",
            "quais informações você pode me dar",
            "como você funciona",
            "em que você pode me ajudar",
    })
    void selfReferentialQuestions_shortCircuitToSobreAssistente_withoutCallingTheLlm(String message)
            throws Exception {
        TriagemServiceImpl service = new TriagemServiceImpl(triagemAI);

        TriagemResult result = service.classify(message, "").toCompletableFuture().get();

        assertEquals(TriagemResult.Decisao.SOBRE_ASSISTENTE, result.getDecisao());
        // The whole point is skipping the LLM call entirely — cheaper and immune to
        // classification noise for this class of question.
        verifyNoInteractions(triagemAI);
    }

    @Test
    void academicQuestionMentioningServe_doesNotFalsePositive() throws Exception {
        // "para que serve o abono de faltas" must NOT match the self-referential patterns:
        // there is no "você"/"vc"/"tu" tied to "serve", so it has to reach the LLM.
        when(triagemAI.classify(any(), any()))
                .thenReturn("{\"decisao\":\"AUTO_RESPONDER\",\"urgencia\":\"BAIXA\",\"camposFaltantes\":\"\"}");
        TriagemServiceImpl service = new TriagemServiceImpl(triagemAI);

        TriagemResult result = service.classify("para que serve o abono de faltas?", "")
                .toCompletableFuture().get();

        assertEquals(TriagemResult.Decisao.AUTO_RESPONDER, result.getDecisao());
        verify(triagemAI).classify(any(), any());
    }

    @Test
    void nonSelfReferentialQuestion_stillGoesThroughTheLlm() throws Exception {
        when(triagemAI.classify(any(), any()))
                .thenReturn("{\"decisao\":\"AUTO_RESPONDER\",\"urgencia\":\"BAIXA\",\"camposFaltantes\":\"\"}");
        TriagemServiceImpl service = new TriagemServiceImpl(triagemAI);

        TriagemResult result = service.classify("como funciona o TCC?", "").toCompletableFuture().get();

        assertEquals(TriagemResult.Decisao.AUTO_RESPONDER, result.getDecisao());
        verify(triagemAI).classify(any(), any());
    }
}
