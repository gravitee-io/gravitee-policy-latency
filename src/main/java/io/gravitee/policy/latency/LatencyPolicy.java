/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.latency;

import static io.gravitee.policy.latency.configuration.LatencyTimeResolver.INVALID_TIME_KEY;
import static io.gravitee.policy.latency.configuration.LatencyTimeResolver.INVALID_TIME_MESSAGE;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.gravitee.policy.latency.configuration.LatencyTimeResolver;
import io.gravitee.policy.latency.configuration.LatencyTimeResolver.InvalidLatencyTimeException;
import io.gravitee.policy.latency.v3.LatencyPolicyV3;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume Lamirand (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LatencyPolicy extends LatencyPolicyV3 implements Policy {

    public LatencyPolicy(final LatencyPolicyConfiguration latencyPolicyConfiguration) {
        super(latencyPolicyConfiguration);
    }

    @Override
    public String id() {
        return "latency";
    }

    @Override
    public Completable onRequest(final HttpExecutionContext ctx) {
        return delay(ctx);
    }

    @Override
    public Completable onResponse(final HttpExecutionContext ctx) {
        return delay(ctx);
    }

    @Override
    public Completable onMessageRequest(final MessageExecutionContext ctx) {
        return ctx.request().onMessage(message -> delay(ctx, message));
    }

    @Override
    public Completable onMessageResponse(final MessageExecutionContext ctx) {
        return ctx.response().onMessage(message -> delay(ctx, message));
    }

    private Completable delay(final HttpExecutionContext ctx) {
        return resolveTime(ctx.getTemplateEngine())
            .flatMapCompletable(time -> Completable.complete().delay(time, configuration.getTimeUnit()))
            .onErrorResumeNext(throwable -> {
                if (throwable instanceof InvalidLatencyTimeException) {
                    return ctx.interruptWith(invalidTimeFailure());
                }
                return Completable.error(throwable);
            });
    }

    private Maybe<Message> delay(final MessageExecutionContext ctx, final Message message) {
        return resolveTime(ctx.getTemplateEngine(message))
            .flatMapMaybe(time -> Maybe.just(message).delay(time, configuration.getTimeUnit()))
            .onErrorResumeNext(throwable -> {
                if (throwable instanceof InvalidLatencyTimeException) {
                    return ctx.interruptMessageWith(invalidTimeFailure());
                }
                return Maybe.error(throwable);
            });
    }

    private Single<Long> resolveTime(final TemplateEngine templateEngine) {
        return LatencyTimeResolver.resolve(configuration, templateEngine);
    }

    private static ExecutionFailure invalidTimeFailure() {
        return new ExecutionFailure(HttpStatusCode.INTERNAL_SERVER_ERROR_500).key(INVALID_TIME_KEY).message(INVALID_TIME_MESSAGE);
    }
}
