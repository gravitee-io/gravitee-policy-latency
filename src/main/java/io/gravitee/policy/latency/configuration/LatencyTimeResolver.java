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
package io.gravitee.policy.latency.configuration;

import io.gravitee.el.TemplateEngine;
import io.reactivex.rxjava3.core.Single;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Resolves the time to wait: the EL expression of {@code dynamicTime} when set, {@code time} otherwise.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LatencyTimeResolver {

    public static final String INVALID_TIME_KEY = "LATENCY_INVALID_TIME";
    public static final String INVALID_TIME_MESSAGE = "Invalid latency time";

    /**
     * Resolves the time asynchronously, failing with an {@link InvalidLatencyTimeException} when the dynamic time is not a number greater than or equal to 0.
     */
    public static Single<Long> resolve(final LatencyPolicyConfiguration configuration, final TemplateEngine templateEngine) {
        final String dynamicTime = configuration.getDynamicTime();
        if (dynamicTime == null || dynamicTime.isBlank()) {
            return Single.just(configuration.getTime());
        }
        return templateEngine
            .eval(dynamicTime, Long.class)
            .switchIfEmpty(Single.error(() -> new InvalidLatencyTimeException(dynamicTime, null)))
            .map(value -> validate(dynamicTime, value))
            .onErrorResumeNext(throwable ->
                Single.error(
                    throwable instanceof InvalidLatencyTimeException ? throwable : new InvalidLatencyTimeException(dynamicTime, throwable)
                )
            );
    }

    /**
     * Resolves the time synchronously, throwing an {@link InvalidLatencyTimeException} when the dynamic time is not a number greater than or equal to 0.
     */
    public static long resolveNow(final LatencyPolicyConfiguration configuration, final TemplateEngine templateEngine) {
        final String dynamicTime = configuration.getDynamicTime();
        if (dynamicTime == null || dynamicTime.isBlank()) {
            return configuration.getTime();
        }
        final Long value;
        try {
            value = templateEngine.getValue(dynamicTime, Long.class);
        } catch (Exception e) {
            throw new InvalidLatencyTimeException(dynamicTime, e);
        }
        if (value == null) {
            throw new InvalidLatencyTimeException(dynamicTime, null);
        }
        return validate(dynamicTime, value);
    }

    private static long validate(final String dynamicTime, final long value) {
        if (value < 0) {
            throw new InvalidLatencyTimeException(dynamicTime, null);
        }
        return value;
    }

    public static class InvalidLatencyTimeException extends RuntimeException {

        InvalidLatencyTimeException(final String dynamicTime, final Throwable cause) {
            super("Unable to resolve a latency time greater than or equal to 0 from '" + dynamicTime + "'", cause);
        }
    }
}
