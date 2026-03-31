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
import java.util.function.Supplier;

/**
 * A {@link Flow.Publisher} that resolves an async setup pipeline
 * ({@link CompletionStage}) before delegating to the inner publisher.
 *
 * <p>Usage pattern in use cases:
 * <pre>{@code
 * return new DeferredPublisher<>(() ->
 *     asyncStep1()
 *         .thenCompose(a -> asyncStep2(a))
 *         .thenApply(b -> innerPublisher(b))
 * );
 * }</pre>
 *
 * <p>Pure Java — no framework dependencies.
 */
public final class DeferredPublisher<T> implements Flow.Publisher<T> {

    /** Factory that resolves the inner publisher asynchronously. */
    private final Supplier<CompletionStage<? extends Flow.Publisher<T>>> factory;

    /**
     * Creates a DeferredPublisher backed by the given factory.
     *
     * @param factory supplier that produces the async setup stage
     */
    public DeferredPublisher(Supplier<CompletionStage<? extends Flow.Publisher<T>>> factory) {
        this.factory = factory;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        CompletionStage<? extends Flow.Publisher<T>> stage;
        try {
            stage = factory.get();
        } catch (Throwable t) {
            subscriber.onSubscribe(noopSubscription());
            subscriber.onError(t);
            return;
        }

        stage.whenComplete((publisher, error) -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException ce
                        ? ce.getCause() : error;
                subscriber.onSubscribe(noopSubscription());
                subscriber.onError(cause);
            } else if (publisher == null) {
                subscriber.onSubscribe(noopSubscription());
                subscriber.onError(new NullPointerException("Publisher resolved to null"));
            } else {
                publisher.subscribe(new Flow.Subscriber<T>() {
                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(T item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
            }
        });
    }

    /**
     * Returns a no-op subscription that ignores request and cancel signals.
     *
     * @return a no-op {@link Flow.Subscription}
     */
    private static Flow.Subscription noopSubscription() {
        return new Flow.Subscription() {
            @Override public void request(long n) {}
            @Override public void cancel() {}
        };
    }
}
