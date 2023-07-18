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

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.reporter.FakeReporter;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.gravitee.reporter.api.http.Metrics;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest(v2ExecutionMode = ExecutionMode.V3)
@DeployApi("/apis/latency-v2.json")
class LatencyPolicyIntegrationV3Test extends AbstractPolicyTest<LatencyPolicy, LatencyPolicyConfiguration> {

    @Test
    void should_apply_latency_on_request(HttpClient httpClient, VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint fakeReporterCheckpoint = vertxTestContext.checkpoint();
        FakeReporter fakeReporter = getBean(FakeReporter.class);
        AtomicReference<Metrics> metricsRef = new AtomicReference<>();
        fakeReporter.setReportableHandler(reportable -> {
            fakeReporterCheckpoint.flag();
            metricsRef.set((Metrics) reportable);
        });

        wiremock.stubFor(get("/endpoint").willReturn(ok("I'm the backend")));

        Checkpoint responseCheckpoint = vertxTestContext.checkpoint();
        TestObserver<Buffer> testObserver = httpClient
            .request(HttpMethod.GET, "/test")
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.rxBody();
            })
            .test();
        awaitTerminalEvent(testObserver);
        testObserver
            .assertComplete()
            .assertValue(body -> {
                responseCheckpoint.flag();
                assertThat(body).hasToString("I'm the backend");
                return true;
            });

        assertThat(vertxTestContext.awaitCompletion(30, TimeUnit.SECONDS)).isTrue();
        wiremock.verify(exactly(1), getRequestedFor(urlPathEqualTo("/endpoint")));
        assertThat(metricsRef.get().getProxyResponseTimeMs()).isGreaterThan(2000);
    }
}
