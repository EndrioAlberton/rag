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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps an upstream publisher and emits a final appendix token before completing.
 * Pure Java — useful for forcing citations/notes at end of SSE streams.
 */
public final class AppendOnCompletePublisher implements Flow.Publisher<String> {

    private final Flow.Publisher<String> upstream;
    private final String appendix;

    public AppendOnCompletePublisher(Flow.Publisher<String> upstream, String appendix) {
        this.upstream = Objects.requireNonNull(upstream, "upstream");
        this.appendix = appendix == null ? "" : appendix;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super String> subscriber) {
        upstream.subscribe(new Flow.Subscriber<String>() {
            private Flow.Subscription upstreamSub;
            private final AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.upstreamSub = subscription;
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        upstreamSub.request(n);
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                        upstreamSub.cancel();
                    }
                });
            }

            @Override
            public void onNext(String item) {
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                if (!cancelled.get() && !appendix.isBlank()) {
                    subscriber.onNext(appendix);
                }
                subscriber.onComplete();
            }
        });
    }
}

