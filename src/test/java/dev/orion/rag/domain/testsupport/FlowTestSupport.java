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

package dev.orion.rag.domain.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Helpers for exercising {@link Flow.Publisher} chains in unit tests.
 */
public final class FlowTestSupport {

    private FlowTestSupport() {
    }

    /**
     * Subscribes once, requests unbounded demand, and collects all string items until completion.
     *
     * @param publisher upstream publisher
     * @return future completing with emitted items, or failing with {@code onError}
     */
    public static CompletableFuture<List<String>> collectAll(Flow.Publisher<String> publisher) {
        CompletableFuture<List<String>> done = new CompletableFuture<>();
        List<String> buffer = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                buffer.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(buffer);
            }
        });
        return done;
    }

    /**
     * Emits the given parts in order then completes (single {@code request} burst is enough).
     */
    public static Flow.Publisher<String> emitTokens(String... parts) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean finished;

            @Override
            public void request(long n) {
                if (finished) {
                    return;
                }
                finished = true;
                for (String p : parts) {
                    subscriber.onNext(p);
                }
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
                finished = true;
            }
        });
    }
}
