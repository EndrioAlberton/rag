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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A {@link Flow.Publisher} that wraps an upstream string publisher,
 * accumulates every emitted token, and — when the upstream completes —
 * executes an async callback (e.g. persist the full response + write audit log)
 * before propagating {@code onComplete} to the downstream subscriber.
 *
 * <p>If the callback fails, the error is logged and {@code onComplete} is
 * still delivered so the SSE stream closes cleanly.
 *
 * <p>Pure Java — no framework dependencies.
 */
public final class AccumulatingOnCompletePublisher implements Flow.Publisher<String> {

    private static final Logger LOG =
            Logger.getLogger(AccumulatingOnCompletePublisher.class.getName());

    /** Upstream publisher whose tokens are forwarded and accumulated. */
    private final Flow.Publisher<String> upstream;
    /** Callback invoked with the full accumulated text after the upstream completes. */
    private final Function<String, CompletionStage<Void>> onCompleteCallback;

    /**
     * Creates an AccumulatingOnCompletePublisher.
     *
     * @param upstream           the stream of tokens to wrap
     * @param onCompleteCallback async callback invoked with the full accumulated text on completion
     */
    public AccumulatingOnCompletePublisher(
            Flow.Publisher<String> upstream,
            Function<String, CompletionStage<Void>> onCompleteCallback) {
        this.upstream = upstream;
        this.onCompleteCallback = onCompleteCallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super String> subscriber) {
        upstream.subscribe(new Flow.Subscriber<String>() {

            private final StringBuilder accumulator = new StringBuilder();
            private final AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void onSubscribe(Flow.Subscription upstream) {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        upstream.request(n);
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                        upstream.cancel();
                    }
                });
            }

            @Override
            public void onNext(String token) {
                accumulator.append(token);
                subscriber.onNext(token);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                if (cancelled.get()) {
                    subscriber.onComplete();
                    return;
                }
                String fullText = accumulator.toString();
                try {
                    onCompleteCallback.apply(fullText).whenComplete((v, err) -> {
                        if (err != null) {
                            LOG.warning("Cleanup callback failed after streaming: " + err.getMessage());
                        }
                        subscriber.onComplete();
                    });
                } catch (Throwable t) {
                    LOG.warning("Cleanup callback threw synchronously: " + t.getMessage());
                    subscriber.onComplete();
                }
            }
        });
    }
}
