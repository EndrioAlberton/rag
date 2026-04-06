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

package dev.orion.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AIRequestTest {

    @Test
    void threeArgConstructorLeavesHistoryEmpty() {
        AIRequest req = new AIRequest("sess", "prompt", "ctx");
        assertEquals("sess", req.getSession());
        assertEquals("prompt", req.getPrompt());
        assertEquals("ctx", req.getContext());
        assertEquals("", req.getHistory());
    }

    @Test
    void fourArgConstructorIncludesHistory() {
        AIRequest req = new AIRequest("sess", "p", "c", "past");
        assertEquals("past", req.getHistory());
    }
}
