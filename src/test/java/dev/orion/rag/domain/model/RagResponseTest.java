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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagResponseTest {

    @Test
    void getFirstContext_returnsEmptyStringWhenNoContexts() {
        RagResponse r = new RagResponse("q", List.of(), 0.0);
        assertEquals("", r.getFirstContext());
    }

    @Test
    void getFirstContext_returnsFirstElement() {
        RagResponse r = new RagResponse("q", List.of("a", "b"), 0.5);
        assertEquals("a", r.getFirstContext());
    }

    @Test
    void accessorsExposeConstructorValues() {
        RagResponse r = new RagResponse("hello", List.of("ctx"), 0.9);
        assertEquals("hello", r.getQuery());
        assertEquals(List.of("ctx"), r.getContexts());
        assertEquals(0.9, r.getScore());
    }
}
