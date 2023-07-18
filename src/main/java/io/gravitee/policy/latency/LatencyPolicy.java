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

import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.gravitee.policy.latency.v3.LatencyPolicyV3;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

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
        return Completable.complete().delay(configuration.getTime(), configuration.getTimeUnit());
    }

    @Override
    public Completable onMessageRequest(final MessageExecutionContext ctx) {
        return ctx.request().onMessage(message -> Maybe.just(message).delay(configuration.getTime(), configuration.getTimeUnit()));
    }

    @Override
    public Completable onMessageResponse(final MessageExecutionContext ctx) {
        return ctx.response().onMessage(message -> Maybe.just(message).delay(configuration.getTime(), configuration.getTimeUnit()));
    }
}
