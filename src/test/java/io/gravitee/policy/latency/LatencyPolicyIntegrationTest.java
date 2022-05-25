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
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.gravitee.reporter.api.http.Metrics;
import io.reactivex.observers.TestObserver;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@DeployApi("/apis/latency.json")
class LatencyPolicyIntegrationTest extends AbstractPolicyTest<LatencyPolicy, LatencyPolicyConfiguration> {

    @Test
    @DisplayName("Should apply latency")
    void shouldApplyLatency(WebClient webClient) {
        FakeReporter fakeReporter = getBean(FakeReporter.class);
        AtomicReference<Metrics> metricsRef = new AtomicReference<>();
        fakeReporter.setReportableHandler(reportable -> {
            metricsRef.set((Metrics) reportable);
        });

        wiremock.stubFor(get("/endpoint").willReturn(ok("I'm the backend")));

        final TestObserver<HttpResponse<Buffer>> obs = webClient.get("/test").rxSend().test();
        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.bodyAsString()).isEqualTo("I'm the backend");
                return true;
            });

        wiremock.verify(exactly(1), getRequestedFor(urlPathEqualTo("/endpoint")));
        assertThat(metricsRef.get().getProxyResponseTimeMs()).isGreaterThan(2000);
    }
}
