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

package dev.orion.rag.infrastructure;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Utility class to wrap blocking operations and make them non-blocking.
 *
 * <p>Executes blocking operations on executor thread and ensures the result
 * is emitted back on the original Vert.x EventLoop thread, allowing
 * subsequent reactive operations to work correctly.
 */
public final class BlockingToReactive {

    /** Quarkus-managed worker thread pool used to offload blocking operations off the event loop. */
    private static final Executor EXECUTOR =
            io.quarkus.runtime.ExecutorRecorder.getCurrent();

    private BlockingToReactive() {
    }

    /**
     * Wraps a blocking operation and returns a Uni that:
     * 1. Executes the operation on executor thread (non-blocking for EventLoop)
     * 2. Emits the result back on the original EventLoop thread
     *
     * @param blockingOperation the blocking operation to execute
     * @return a Uni that emits the result on the EventLoop thread
     */
    public static <T> Uni<T> wrap(Supplier<T> blockingOperation) {
        // Capture Vert.x context before blocking work; restore on EventLoop
        // after.
        Context vertxContext = Vertx.currentContext();

        return Uni.createFrom().item(() -> blockingOperation.get())
                .runSubscriptionOn(EXECUTOR)
                .onItem().transformToUni(result -> {
                    if (vertxContext != null) {
                        return Uni.createFrom().emitter(emitter -> {
                            vertxContext.runOnContext(v -> emitter.complete(result));
                        });
                    }
                    return Uni.createFrom().item(result);
                });
    }

    /**
     * Wraps a blocking operation that may throw checked exceptions.
     *
     * @param blockingOperation the blocking operation that may throw exceptions
     * @return a Uni that emits the result on the EventLoop thread
     */
    public static <T> Uni<T> wrapThrowing(
            java.util.concurrent.Callable<T> blockingOperation) {
        Context vertxContext = Vertx.currentContext();

        return Uni.createFrom().item(() -> {
                    try {
                        return blockingOperation.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .runSubscriptionOn(EXECUTOR)
                .onItem().transformToUni(result -> {
                    if (vertxContext != null) {
                        return Uni.createFrom().emitter(emitter -> {
                            vertxContext.runOnContext(v -> {
                                emitter.complete(result);
                            });
                        });
                    }
                    return Uni.createFrom().item(result);
                });
    }
}
