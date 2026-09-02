/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.injection.HttpRequestSmugglingAttackGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T16: Test HTTP request smuggling patterns
 *
 * <p>
 * This test class implements Task T16 from the HTTP security validation plan,
 * focusing on testing HTTP request smuggling attacks that attempt to bypass
 * security controls through HTTP protocol manipulation using specialized
 * generators and comprehensive attack vectors.
 * </p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>Content-Length/Transfer-Encoding (CL.TE) smuggling attacks</li>
 *   <li>Transfer-Encoding/Content-Length (TE.CL) smuggling attacks</li>
 *   <li>Transfer-Encoding/Transfer-Encoding (TE.TE) smuggling attacks</li>
 *   <li>HTTP pipeline poisoning attacks</li>
 *   <li>Cache deception through request manipulation</li>
 *   <li>HTTP response queue poisoning</li>
 *   <li>Authentication bypass through smuggling</li>
 *   <li>Backend server confusion attacks</li>
 *   <li>Content-Length manipulation attacks</li>
 *   <li>Transfer-Encoding obfuscation patterns</li>
 *   <li>HTTP/1.1 vs HTTP/2 downgrade smuggling</li>
 *   <li>Chunk encoding manipulation</li>
 *   <li>Double Content-Length header attacks</li>
 *   <li>Mixed HTTP method smuggling</li>
 *   <li>Header parsing differential attacks</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 7230 - HTTP/1.1 Message Syntax and Routing</li>
 *   <li>RFC 7231 - HTTP/1.1 Semantics and Content</li>
 *   <li>OWASP - HTTP Request Smuggling</li>
 *   <li>CVE-2019-9516, CVE-2019-9518, CVE-2020-11080 (Request smuggling CVEs)</li>
 *   <li>CWE-444 - Inconsistent Interpretation of HTTP Requests</li>
 *   <li>PortSwigger Web Security Academy - HTTP Request Smuggling</li>
 * </ul>
 *
 * Implements: Task T16 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T16: HTTP Request Smuggling Attack Tests")
class HttpRequestSmugglingAttackTest {

    /**
     * Sample counters for the six guarded parameterized tests. Each of those tests opens with an
     * early-return filter that keeps only the attack shape it is about, because the shared
     * {@link HttpRequestSmugglingAttackGenerator} emits every smuggling family. That filter is only
     * sound while it still admits samples: if the generator ever stops producing the filtered
     * shape, the guard would swallow every sample and the test would pass while asserting nothing.
     * The {@link #shouldHaveAdmittedFilteredSamples()} check turns that silent degradation into a
     * failure.
     */
    private static final AtomicInteger CL_TE_SAMPLES = new AtomicInteger();
    private static final AtomicInteger CL_TE_ADMITTED = new AtomicInteger();
    private static final AtomicInteger TE_CL_SAMPLES = new AtomicInteger();
    private static final AtomicInteger TE_CL_ADMITTED = new AtomicInteger();
    private static final AtomicInteger TE_TE_SAMPLES = new AtomicInteger();
    private static final AtomicInteger TE_TE_ADMITTED = new AtomicInteger();
    private static final AtomicInteger PIPELINE_SAMPLES = new AtomicInteger();
    private static final AtomicInteger PIPELINE_ADMITTED = new AtomicInteger();
    private static final AtomicInteger CACHE_SAMPLES = new AtomicInteger();
    private static final AtomicInteger CACHE_ADMITTED = new AtomicInteger();
    private static final AtomicInteger DOUBLE_CL_SAMPLES = new AtomicInteger();
    private static final AtomicInteger DOUBLE_CL_ADMITTED = new AtomicInteger();

    /**
     * Family-fingerprint patterns matched against the exact header shapes produced by
     * {@code HttpRequestSmugglingAttackGenerator}. Each guard below verifies header ORDER and/or
     * MULTIPLICITY rather than a broad substring, so a payload from a different generator branch
     * (e.g. a CL.TE payload, which also contains both header names) cannot be admitted by a
     * differently-named guard.
     */
    private static final Pattern CL_THEN_TE = Pattern.compile("Content-Length: \\d+%0d%0aTransfer-Encoding: chunked");
    private static final Pattern TE_THEN_CL = Pattern.compile("Transfer-Encoding: chunked%0d%0aContent-Length: \\d+");
    private static final Pattern DOUBLE_TRANSFER_ENCODING =
            Pattern.compile("(?i)transfer-encoding:.*?%0d%0atransfer-encoding:");
    private static final Pattern DOUBLE_CONTENT_LENGTH =
            Pattern.compile("Content-Length: \\d+%0d%0aContent-Length: \\d+");
    private static final Pattern PIPELINE_CONNECTION_KEEPALIVE =
            Pattern.compile("Connection: keep-alive%0d%0aContent-Length: \\d+");
    private static final Pattern CACHE_HEADER_THEN_CONTENT_LENGTH =
            Pattern.compile("(?:Cache-Control|Vary|Expires): .*?%0d%0aContent-Length: \\d+");

    /**
     * CL.TE family: exactly the header order the front-end/back-end desync relies on -
     * Content-Length immediately followed by Transfer-Encoding. A TE.CL payload (reversed order)
     * does not match.
     */
    private static boolean isClTeFamily(String attack) {
        return CL_THEN_TE.matcher(attack).find();
    }

    /**
     * TE.CL family: Transfer-Encoding immediately followed by Content-Length - the reverse order
     * of CL.TE, which is precisely the discriminator between the two families.
     */
    private static boolean isTeClFamily(String attack) {
        return TE_THEN_CL.matcher(attack).find();
    }

    /**
     * TE.TE family: two Transfer-Encoding headers (header multiplicity, case-insensitive per the
     * generator's casing variants) and no Content-Length header at all, so a single
     * Transfer-Encoding header (as used by CL.TE, TE.CL, or chunked-encoding-bypass payloads)
     * cannot be admitted.
     */
    private static boolean isTeTeFamily(String attack) {
        return DOUBLE_TRANSFER_ENCODING.matcher(attack).find() && !attack.contains("Content-Length:");
    }

    /**
     * Pipeline poisoning family: the generator's unique "Connection: keep-alive" immediately
     * followed by Content-Length signature. WebSocket-upgrade payloads also use
     * "Connection: keep-alive" but always as "Connection: keep-alive, Upgrade" (no CRLF directly
     * after "keep-alive"), so they do not match.
     */
    private static boolean isPipelinePoisoningFamily(String attack) {
        return PIPELINE_CONNECTION_KEEPALIVE.matcher(attack).find();
    }

    /**
     * Cache deception family: one of the cache-specific headers (Cache-Control, Vary, Expires -
     * used nowhere else in the generator) immediately followed by Content-Length.
     */
    private static boolean isCacheDeceptionFamily(String attack) {
        return CACHE_HEADER_THEN_CONTENT_LENGTH.matcher(attack).find();
    }

    /**
     * Double Content-Length (CL.CL) family: two Content-Length headers adjacent to each other
     * (header multiplicity) and no Transfer-Encoding header, so a CL.TE/TE.CL payload whose
     * embedded smuggled request happens to contain a second, non-adjacent Content-Length string
     * is not admitted.
     */
    private static boolean isDoubleContentLengthFamily(String attack) {
        return DOUBLE_CONTENT_LENGTH.matcher(attack).find() && !attack.contains("Transfer-Encoding:");
    }

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;
    private SecurityConfiguration config;

    @AfterAll
    static void shouldHaveAdmittedFilteredSamples() {
        assertAll("Guarded parameterized tests must not degrade into silent no-ops",
                () -> assertGuardAdmittedSamples("shouldBlockClTeSmuggling",
                        CL_TE_SAMPLES, CL_TE_ADMITTED),
                () -> assertGuardAdmittedSamples("shouldBlockTeClSmuggling",
                        TE_CL_SAMPLES, TE_CL_ADMITTED),
                () -> assertGuardAdmittedSamples("shouldBlockTeTeSmuggling",
                        TE_TE_SAMPLES, TE_TE_ADMITTED),
                () -> assertGuardAdmittedSamples("shouldBlockPipelinePoisoning",
                        PIPELINE_SAMPLES, PIPELINE_ADMITTED),
                () -> assertGuardAdmittedSamples("shouldBlockCacheDeception",
                        CACHE_SAMPLES, CACHE_ADMITTED),
                () -> assertGuardAdmittedSamples("shouldBlockDoubleContentLength",
                        DOUBLE_CL_SAMPLES, DOUBLE_CL_ADMITTED));
    }

    /**
     * Asserts that a guarded test admitted at least one sample, but only when that test actually
     * ran. Skipping the assertion for a test with zero samples keeps a single-method IDE run from
     * failing on the sibling methods it never executed.
     */
    private static void assertGuardAdmittedSamples(String testName, AtomicInteger samples, AtomicInteger admitted) {
        if (samples.get() == 0) {
            return;
        }
        assertTrue(admitted.get() > 0,
                () -> testName + " saw " + samples.get() + " generated samples but its pattern guard "
                        + "admitted none — the test asserted nothing this run");
    }

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Test comprehensive HTTP request smuggling attack patterns.
     *
     * <p>
     * Uses HttpRequestSmugglingAttackGenerator which provides 15 different types
     * of request smuggling attacks including CL.TE, TE.CL, TE.TE, pipeline
     * poisoning, cache deception, and other HTTP protocol manipulation attacks.
     * </p>
     *
     * @param smugglingAttackPattern A request smuggling attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("All HTTP request smuggling attacks should be rejected")
    void shouldRejectAllHttpRequestSmugglingAttacks(String smugglingAttackPattern) {
        // Given: A request smuggling attack pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the smuggling attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(smugglingAttackPattern),
                "Request smuggling attack should be rejected: " + smugglingAttackPattern);

        // Then: The validation should fail with appropriate security event
        assertNotNull(exception, "Exception should be thrown for request smuggling attack");
        assertTrue(isSpecificRequestSmugglingFailure(exception.getFailureType()),
                "Failure type should be specific request smuggling related: " + exception.getFailureType() +
                        " for pattern: " + smugglingAttackPattern);

        // And: Original malicious input should be preserved
        assertEquals(smugglingAttackPattern, exception.getOriginalInput(),
                "Original input should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for: " + smugglingAttackPattern);
    }

    /**
     * Test specific CL.TE (Content-Length/Transfer-Encoding) smuggling attacks.
     *
     * <p>
     * Tests attacks where the front-end server processes the Content-Length
     * header while the back-end server processes the Transfer-Encoding header,
     * creating desynchronization opportunities. Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("CL.TE smuggling attacks must be blocked")
    void shouldBlockClTeSmuggling(String clTeAttack) {
        // Filter to test only CL.TE patterns: Content-Length immediately followed by Transfer-Encoding
        CL_TE_SAMPLES.incrementAndGet();
        if (!isClTeFamily(clTeAttack)) {
            return; // Skip non-CL.TE-order patterns
        }
        CL_TE_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(clTeAttack),
                "CL.TE smuggling attack should be rejected: " + clTeAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "CL.TE smuggling should trigger INVALID_CHARACTER detection for: " + clTeAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for CL.TE attack");
    }

    /**
     * Test specific TE.CL (Transfer-Encoding/Content-Length) smuggling attacks.
     *
     * <p>
     * Tests attacks where the front-end server processes the Transfer-Encoding
     * header while the back-end server processes the Content-Length header.
     * Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("TE.CL smuggling attacks must be blocked")
    void shouldBlockTeClSmuggling(String teClAttack) {
        // Filter to test only TE.CL patterns: Transfer-Encoding immediately followed by Content-Length
        TE_CL_SAMPLES.incrementAndGet();
        if (!isTeClFamily(teClAttack)) {
            return; // Skip non-TE.CL-order patterns
        }
        TE_CL_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(teClAttack),
                "TE.CL smuggling attack should be rejected: " + teClAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "TE.CL smuggling should trigger INVALID_CHARACTER detection for: " + teClAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for TE.CL attack");
    }

    /**
     * Test specific TE.TE (Transfer-Encoding/Transfer-Encoding) smuggling attacks.
     *
     * <p>
     * Tests attacks using Transfer-Encoding header obfuscation to create
     * parsing differences between front-end and back-end servers.
     * Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("TE.TE smuggling attacks must be blocked")
    void shouldBlockTeTeSmuggling(String teTeAttack) {
        // Filter to test only TE.TE patterns: two Transfer-Encoding headers, no Content-Length
        TE_TE_SAMPLES.incrementAndGet();
        if (!isTeTeFamily(teTeAttack)) {
            return; // Skip patterns without duplicated Transfer-Encoding headers
        }
        TE_TE_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(teTeAttack),
                "TE.TE smuggling attack should be rejected: " + teTeAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "TE.TE smuggling should trigger INVALID_CHARACTER detection for: " + teTeAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for TE.TE attack");
    }

    /**
     * Test HTTP pipeline poisoning attacks.
     *
     * <p>
     * Tests attacks that attempt to poison HTTP connection pipelines
     * to affect subsequent requests from other users. Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("HTTP pipeline poisoning attacks must be blocked")
    void shouldBlockPipelinePoisoning(String pipelinePoisoningAttack) {
        // Filter to test only pipeline-poisoning patterns: Connection: keep-alive then Content-Length
        PIPELINE_SAMPLES.incrementAndGet();
        if (!isPipelinePoisoningFamily(pipelinePoisoningAttack)) {
            return; // Skip patterns without the keep-alive pipeline signature
        }
        PIPELINE_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(pipelinePoisoningAttack),
                "Pipeline poisoning attack should be rejected: " + pipelinePoisoningAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "Pipeline poisoning should trigger INVALID_CHARACTER detection for: " + pipelinePoisoningAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for pipeline poisoning");
    }

    /**
     * Test cache deception through request smuggling.
     *
     * <p>
     * Tests attacks that use request smuggling to manipulate caching
     * behavior and serve malicious content to other users. Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("Cache deception attacks must be blocked")
    void shouldBlockCacheDeception(String cacheDeceptionAttack) {
        // Filter to test only cache-deception patterns: a cache header then Content-Length
        CACHE_SAMPLES.incrementAndGet();
        if (!isCacheDeceptionFamily(cacheDeceptionAttack)) {
            return; // Skip patterns without the cache-header + Content-Length signature
        }
        CACHE_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(cacheDeceptionAttack),
                "Cache deception attack should be rejected: " + cacheDeceptionAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "Cache deception should trigger INVALID_CHARACTER detection for: " + cacheDeceptionAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for cache deception");
    }

    /**
     * Test double Content-Length header attacks.
     *
     * <p>
     * Tests attacks using multiple Content-Length headers to create
     * parsing inconsistencies between servers. Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 200)
    @DisplayName("Double Content-Length header attacks must be blocked")
    void shouldBlockDoubleContentLength(String doubleContentLengthAttack) {
        // Filter to test only CL.CL patterns: two adjacent Content-Length headers, no Transfer-Encoding
        DOUBLE_CL_SAMPLES.incrementAndGet();
        if (!isDoubleContentLengthFamily(doubleContentLengthAttack)) {
            return; // Skip patterns without duplicated adjacent Content-Length headers
        }
        DOUBLE_CL_ADMITTED.incrementAndGet();

        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(doubleContentLengthAttack),
                "Double Content-Length attack should be rejected: " + doubleContentLengthAttack);

        assertNotNull(exception);
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "Double Content-Length should trigger INVALID_CHARACTER detection for: " + doubleContentLengthAttack);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for double Content-Length");
    }

    /**
     * Test comprehensive edge cases in request smuggling detection.
     *
     * <p>
     * Tests various edge cases and corner conditions that might be
     * exploited in request smuggling attacks. Uses generator for dynamic patterns.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 35)
    @DisplayName("Request smuggling edge cases must be handled")
    void shouldHandleRequestSmugglingEdgeCases(String edgeCaseAttack) {
        // Test all generator patterns as potential edge cases
        long initialEventCount = eventCounter.getTotalCount();

        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(edgeCaseAttack),
                "Request smuggling edge case should be rejected: " + edgeCaseAttack);

        assertNotNull(exception);
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for edge case");
    }

    /**
     * Determines if a failure type is specifically appropriate for the given request smuggling attack pattern.
     * Most HTTP request smuggling attacks use CRLF injection (%0d%0a) which should trigger CONTROL_CHARACTERS.
     *
     * @param failureType The failure type to check
     * @return true if the failure type is specifically appropriate for the pattern
     */
    private boolean isSpecificRequestSmugglingFailure(UrlSecurityFailureType failureType) {
        // HTTP Request Smuggling patterns can trigger multiple specific failure types:
        // - CRLF injection (%0d%0a, %0a) → CONTROL_CHARACTERS or INVALID_CHARACTER
        // - Malformed chunk encoding → INVALID_ENCODING
        // - HTTP protocol violations → PROTOCOL_VIOLATION
        // - RFC violations → RFC_VIOLATION
        // - General malformed input → MALFORMED_INPUT

        // Accept these specific failure types as valid for request smuggling patterns
        return failureType == UrlSecurityFailureType.CONTROL_CHARACTERS ||
                failureType == UrlSecurityFailureType.INVALID_CHARACTER ||
                failureType == UrlSecurityFailureType.PROTOCOL_VIOLATION ||
                failureType == UrlSecurityFailureType.RFC_VIOLATION ||
                failureType == UrlSecurityFailureType.INVALID_ENCODING ||
                failureType == UrlSecurityFailureType.MALFORMED_INPUT ||
                failureType == UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED;
    }

}