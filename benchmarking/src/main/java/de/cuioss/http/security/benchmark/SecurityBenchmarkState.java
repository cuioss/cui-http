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
package de.cuioss.http.security.benchmark;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.http.security.pipeline.PipelineFactory.PipelineSet;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.http.security.validation.DecodingStage;
import de.cuioss.http.security.validation.LengthValidationStage;
import de.cuioss.http.security.validation.NormalizationStage;
import de.cuioss.http.security.validation.PatternMatchingStage;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared JMH state for all security benchmark classes.
 * Creates pipelines and validation stages once per trial, cycling through
 * pre-defined test data arrays for each invocation.
 */
@State(Scope.Thread)
@SuppressWarnings("java:S112")
public class SecurityBenchmarkState {

    private static final CuiLogger LOGGER = new CuiLogger(SecurityBenchmarkState.class);

    // Pipelines
    public PipelineSet pipelines;

    // Individual stages for isolation benchmarks
    public PatternMatchingStage patternMatchingStage;
    public DecodingStage decodingStage;
    public CharacterValidationStage characterValidationStage;
    public NormalizationStage normalizationStage;
    public LengthValidationStage lengthValidationStage;

    // Test data arrays
    private static final String[] CLEAN_URLS = {
            "/api/users/123",
            "/api/products/456/details",
            "/health/ready",
            "/v2/search/results",
            "/static/images/logo.png",
            "/api/v1/orders/789/items",
            "/docs/getting-started",
            "/api/config/settings",
            "/public/assets/style.css",
            "/api/users/profile/avatar"
    };

    private static final String[] ATTACK_URLS = {
            "/../../../etc/passwd",
            "/api/users/<script>alert(1)</script>",
            "/api/%2e%2e/%2e%2e/etc/shadow",
            "/api/..;/admin/config",
            "/api/users/123%00.html",
            "/cgi-bin/.%2e/.%2e/.%2e/etc/passwd",
            "/api/%252e%252e/admin",
            "/api/users/../../windows/system32",
            "/api/../conf/server.xml",
            "/api/v1/%c0%ae%c0%ae/etc/passwd"
    };

    private static final String[] CLEAN_PARAMS = {
            "search_query",
            "page1size20",
            "filter-active",
            "id-abc-123-def",
            "lang-en-format-json",
            "offset0-limit50",
            "category-electronics",
            "token-abc123def456",
            "redirect-dashboard",
            "name-john-doe"
    };

    private static final String[] CLEAN_HEADERS = {
            "application/json",
            "text/html; charset=utf-8",
            "Bearer eyJhbGciOiJIUzI1NiJ9",
            "Mozilla/5.0 (compatible)",
            "gzip, deflate, br",
            "keep-alive",
            "en-US,en;q=0.9",
            "no-cache",
            "max-age=3600",
            "application/x-www-form-urlencoded"
    };

    private final AtomicInteger urlIndex = new AtomicInteger(0);
    private final AtomicInteger attackIndex = new AtomicInteger(0);
    private final AtomicInteger paramIndex = new AtomicInteger(0);
    private final AtomicInteger headerIndex = new AtomicInteger(0);

    @Setup(Level.Trial)
    public void setup() {
        LOGGER.debug("Setting up security benchmark state");

        var config = SecurityConfiguration.builder().build();
        var counter = new SecurityEventCounter();

        // Create all pipelines via factory
        pipelines = PipelineFactory.createCommonPipelines(config, counter);

        // Create individual stages for isolation benchmarks
        patternMatchingStage = new PatternMatchingStage(config, ValidationType.URL_PATH);
        decodingStage = new DecodingStage(config, ValidationType.URL_PATH);
        characterValidationStage = new CharacterValidationStage(config, ValidationType.URL_PATH);
        normalizationStage = new NormalizationStage(config, ValidationType.URL_PATH);
        lengthValidationStage = new LengthValidationStage(config, ValidationType.URL_PATH);

        LOGGER.debug("Security benchmark state setup completed");
    }

    /** Returns the next clean URL, cycling through the array. */
    public String nextCleanUrl() {
        return CLEAN_URLS[urlIndex.getAndIncrement() % CLEAN_URLS.length];
    }

    /** Returns the next attack URL, cycling through the array. */
    public String nextAttackUrl() {
        return ATTACK_URLS[attackIndex.getAndIncrement() % ATTACK_URLS.length];
    }

    /** Returns the next clean parameter, cycling through the array. */
    public String nextCleanParam() {
        return CLEAN_PARAMS[paramIndex.getAndIncrement() % CLEAN_PARAMS.length];
    }

    /** Returns the next clean header value, cycling through the array. */
    public String nextCleanHeader() {
        return CLEAN_HEADERS[headerIndex.getAndIncrement() % CLEAN_HEADERS.length];
    }
}
