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

package dev.orion.rag.domain.support;

import dev.orion.rag.domain.testsupport.FlowTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeferredPublisherTest {

    @Test
    void forwardsInnerPublisherAfterStageCompletes() throws Exception {
        DeferredPublisher<String> def = new DeferredPublisher<>(() ->
                CompletableFuture.completedFuture(FlowTestSupport.emitTokens("a", "b")));

        assertEquals(List.of("a", "b"), FlowTestSupport.collectAll(def).get());
    }

    @Test
    void propagatesAsyncFailure() {
        DeferredPublisher<String> def = new DeferredPublisher<>(() ->
                CompletableFuture.failedFuture(new IllegalStateException("boom")));

        assertThrows(Exception.class, () -> FlowTestSupport.collectAll(def).get());
    }

    @Test
    void nullPublisherCompletesAsError() {
        DeferredPublisher<String> def = new DeferredPublisher<>(() ->
                CompletableFuture.completedFuture(null));

        assertThrows(Exception.class, () -> FlowTestSupport.collectAll(def).get());
    }
}
