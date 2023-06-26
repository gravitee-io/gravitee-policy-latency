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
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.entrypoint.sse.SseEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.gateway.tests.sdk.reporter.FakeReporter;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.latency.configuration.LatencyPolicyConfiguration;
import io.gravitee.reporter.api.v4.metric.MessageMetrics;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Guillaume Lamirand (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@ExtendWith(VertxExtension.class)
class LatencyPolicyIntegrationV4Test extends AbstractPolicyTest<LatencyPolicy, LatencyPolicyConfiguration> {

    private FakeReporter fakeReporter;
    private AtomicReference<Metrics> metricsRef;
    private AtomicReference<MessageMetrics> messageMetricsRef;

    @AfterEach
    void afterEach() {
        fakeReporter.reset();
    }

    @Override
    public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
        reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
    }

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        entrypoints.putIfAbsent("sse", EntrypointBuilder.build("sse", SseEntrypointConnectorFactory.class));
        entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
    }

    @Test
    @DeployApi("/apis/latency-v4-proxy.json")
    void should_apply_latency_on_proxy_request(HttpClient httpClient, VertxTestContext vertxTestContext) throws InterruptedException {
        prepareReporter(vertxTestContext, 1);
        Checkpoint responseCheckpoint = vertxTestContext.checkpoint();
        wiremock.stubFor(get("/endpoint").willReturn(ok("I'm the backend")));

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

        wiremock.verify(exactly(1), getRequestedFor(urlPathEqualTo("/endpoint")));

        assertThat(vertxTestContext.awaitCompletion(30, TimeUnit.SECONDS)).isTrue();
        Metrics metrics = metricsRef.get();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getGatewayResponseTimeMs()).isGreaterThan(2000);
    }

    @Test
    @DeployApi("/apis/latency-v4-message-publish.json")
    void should_apply_latency_on_request_message(HttpClient httpClient, VertxTestContext vertxTestContext) throws InterruptedException {
        prepareReporter(vertxTestContext, 3);
        Checkpoint responseCheckpoint = vertxTestContext.checkpoint();
        httpClient
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend("message"))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(202);
                return response.body();
            })
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(body -> {
                responseCheckpoint.flag();
                assertThat(body.length()).isZero();
                return true;
            });
        assertThat(vertxTestContext.awaitCompletion(30, TimeUnit.SECONDS)).isTrue();

        MessageMetrics messageMetrics = messageMetricsRef.get();
        assertThat(messageMetrics).isNotNull();
        assertThat(messageMetrics.getGatewayLatencyMs()).isGreaterThan(2000);
    }

    @Test
    @DeployApi("/apis/latency-v4-message-subscribe.json")
    void should_apply_latency_on_response_message(HttpClient httpClient, VertxTestContext vertxTestContext) throws InterruptedException {
        prepareReporter(vertxTestContext, 3);
        Checkpoint responseCheckpoint = vertxTestContext.checkpoint();
        httpClient
            .rxRequest(HttpMethod.GET, "/test")
            .flatMap(request -> {
                request.putHeader(HttpHeaderNames.ACCEPT.toString(), MediaType.TEXT_EVENT_STREAM);
                return request.rxSend();
            })
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.toFlowable();
            })
            .filter(buffer -> !buffer.toString().startsWith("retry:") && !buffer.toString().startsWith(":"))
            .test()
            .awaitCount(1)
            .assertValue(body -> {
                responseCheckpoint.flag();
                return true;
            });
        assertThat(vertxTestContext.awaitCompletion(30, TimeUnit.SECONDS)).isTrue();
        MessageMetrics messageMetrics = messageMetricsRef.get();
        assertThat(messageMetrics).isNotNull();
        assertThat(messageMetrics.getGatewayLatencyMs()).isGreaterThan(2000);
    }

    void prepareReporter(final VertxTestContext vertxTestContext, final int flagCount) {
        Checkpoint fakeReporterCheckpoint = vertxTestContext.checkpoint(flagCount);
        fakeReporter = getBean(FakeReporter.class);
        metricsRef = new AtomicReference<>();
        messageMetricsRef = new AtomicReference<>();
        fakeReporter.setReportableHandler(reportable -> {
            fakeReporterCheckpoint.flag();
            if (reportable instanceof Metrics) {
                metricsRef.set((Metrics) reportable);
            } else if (reportable instanceof MessageMetrics) {
                messageMetricsRef.set((MessageMetrics) reportable);
            }
        });
    }
}
