/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Request-level (collection) validator that enforces the maximum parameter, header, and cookie
 * <em>counts</em> configured in {@link SecurityConfiguration}.
 *
 * <p>The single-value validation pipelines built by {@code PipelineFactory} operate on one value
 * at a time and therefore cannot enforce a count over a whole collection - the missing API that
 * PR #74 (correctly) identified when it removed the unenforced count knobs. This validator supplies
 * that missing collection-level entry point: pass the parameter map, header map, or cookie
 * collection and it rejects the request when a count exceeds its configured limit
 * ({@code maxParameterCount} / {@code maxHeaderCount} / {@code maxCookieCount}).</p>
 *
 * <p>Count limits defend against resource-exhaustion and hash-collision denial-of-service attacks.
 * Defaults come from {@link de.cuioss.http.security.config.SecurityDefaults} (parameters 100,
 * headers 50, cookies 20; strict 20/20/10, lenient 500/100/50).</p>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * SecurityEventCounter counter = new SecurityEventCounter();
 * RequestCollectionValidator validator = new RequestCollectionValidator(config, counter);
 *
 * validator.validateParameters(request.getParameterMap());
 * validator.validateHeaders(request.getHeaderMap());
 * validator.validateCookies(request.getCookies());
 * </pre>
 *
 * <p>Violations throw {@link UrlSecurityException} with failure type
 * {@link UrlSecurityFailureType#TOO_MANY_ELEMENTS} and record an event on the supplied
 * {@link SecurityEventCounter}, consistent with the single-value pipelines.</p>
 *
 * @since 1.0
 */
public final class RequestCollectionValidator {

    private final SecurityConfiguration config;
    private final SecurityEventCounter eventCounter;

    /**
     * Creates a request-collection validator.
     *
     * @param config the security configuration providing the count limits
     * @param eventCounter the counter for recording security violations
     * @throws NullPointerException if either argument is null
     */
    public RequestCollectionValidator(SecurityConfiguration config, SecurityEventCounter eventCounter) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventCounter = Objects.requireNonNull(eventCounter, "eventCounter must not be null");
    }

    /**
     * Validates the number of request parameters against {@code maxParameterCount}.
     *
     * @param parameters the parameter map (keys are parameter names)
     * @throws NullPointerException if {@code parameters} is null
     * @throws UrlSecurityException if the parameter count exceeds the configured maximum
     */
    public void validateParameters(Map<String, ?> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        validateParameterCount(parameters.size());
    }

    /**
     * Validates the number of request headers against {@code maxHeaderCount}.
     *
     * @param headers the header map (keys are header names)
     * @throws NullPointerException if {@code headers} is null
     * @throws UrlSecurityException if the header count exceeds the configured maximum
     */
    public void validateHeaders(Map<String, ?> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        validateHeaderCount(headers.size());
    }

    /**
     * Validates the number of request cookies against {@code maxCookieCount}.
     *
     * @param cookies the cookie collection
     * @throws NullPointerException if {@code cookies} is null
     * @throws UrlSecurityException if the cookie count exceeds the configured maximum
     */
    public void validateCookies(Collection<?> cookies) {
        Objects.requireNonNull(cookies, "cookies must not be null");
        validateCookieCount(cookies.size());
    }

    /**
     * Validates a parameter count against {@code maxParameterCount}.
     *
     * @param count the number of parameters (non-negative)
     * @throws UrlSecurityException if the count exceeds the configured maximum
     */
    public void validateParameterCount(int count) {
        enforce(count, config.maxParameterCount(), ValidationType.PARAMETER_NAME, "parameter");
    }

    /**
     * Validates a header count against {@code maxHeaderCount}.
     *
     * @param count the number of headers (non-negative)
     * @throws UrlSecurityException if the count exceeds the configured maximum
     */
    public void validateHeaderCount(int count) {
        enforce(count, config.maxHeaderCount(), ValidationType.HEADER_NAME, "header");
    }

    /**
     * Validates a cookie count against {@code maxCookieCount}.
     *
     * @param count the number of cookies (non-negative)
     * @throws UrlSecurityException if the count exceeds the configured maximum
     */
    public void validateCookieCount(int count) {
        enforce(count, config.maxCookieCount(), ValidationType.COOKIE_NAME, "cookie");
    }

    private void enforce(int actual, int max, ValidationType validationType, String kind) {
        if (actual > max) {
            eventCounter.increment(UrlSecurityFailureType.TOO_MANY_ELEMENTS);
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.TOO_MANY_ELEMENTS)
                    .validationType(validationType)
                    .originalInput(actual + " " + kind + "s")
                    .detail("Too many " + kind + "s: " + actual + " exceeds maximum of " + max)
                    .build();
        }
    }
}
