/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.latency.v3;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class LatencyPolicyV3 {

    protected final LatencyPolicyConfiguration configuration;

    @OnRequest
    public void onRequest(
        final Request request,
        final Response response,
        final ExecutionContext executionContext,
        final PolicyChain policyChain
    ) {
        executionContext
            .getComponent(Vertx.class)
            .setTimer(configuration.getTimeUnit().toMillis(configuration.getTime()), timerId -> policyChain.doNext(request, response));
    }
}
