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
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.header.HttpHeaderInjectionAttackGenerator.Surface;
import de.cuioss.http.security.generators.injection.HttpRequestSmugglingAttackGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T16: Test HTTP request smuggling patterns
 *
 * <p>
 * HTTP request smuggling exploits a disagreement between a front-end and a back-end server about
 * where one request ends. Every payload reaches the application as the content of one HTTP
 * component, and this class validates it on the surface that receives it:
 * </p>
 * <ul>
 *   <li><strong>Header-shaped families</strong> (CL.TE, TE.CL, TE.TE, CL.CL, HTTP/2 downgrade,
 *       pipeline poisoning, cache deception, WebSocket upgrade, chunked-encoding bypass) inject a
 *       header block behind a header value across raw line breaks. They are validated by
 *       {@link HTTPHeaderValidationPipeline} for {@link ValidationType#HEADER_VALUE}, whose
 *       character stage rejects a raw CR or LF as {@link UrlSecurityFailureType#INVALID_CHARACTER}.</li>
 *   <li><strong>Query-shaped families</strong> (authentication bypass, header manipulation, method
 *       override, URL rewriting, request hijacking, response queue poisoning) carry the line
 *       breaks percent-encoded in a parameter value. They are validated by
 *       {@link URLParameterValidationPipeline} configured to reject line breaks in parameter
 *       values, whose decoding stage rejects the decoded CR as
 *       {@link UrlSecurityFailureType#CONTROL_CHARACTERS}.</li>
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

    /** The verdict of the header-value pipeline for a raw CR or LF. */
    private static final UrlSecurityFailureType RAW_LINE_BREAK_IN_HEADER_VALUE =
            UrlSecurityFailureType.INVALID_CHARACTER;

    /** The verdict of the line-break-rejecting parameter pipeline for a decoded CR or LF. */
    private static final UrlSecurityFailureType DECODED_LINE_BREAK_IN_PARAMETER_VALUE =
            UrlSecurityFailureType.CONTROL_CHARACTERS;

    /**
     * Sample counters for the six guarded parameterized tests. Each of those tests opens with an
     * early-return filter that keeps only the attack shape it is about, because the shared
     * {@link HttpRequestSmugglingAttackGenerator.HeaderShaped} generator emits every header-shaped
     * smuggling family. That filter is only sound while it still admits samples: if the generator
     * ever stops producing the filtered shape, the guard would swallow every sample and the test
     * would pass while asserting nothing. The {@link #shouldHaveAdmittedFilteredSamples()} check
     * turns that silent degradation into a failure.
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
    private static final Pattern CL_THEN_TE = Pattern.compile("Content-Length: \\d+\\r\\nTransfer-Encoding: chunked");
    private static final Pattern TE_THEN_CL = Pattern.compile("Transfer-Encoding: chunked\\r\\nContent-Length: \\d+");
    private static final Pattern DOUBLE_TRANSFER_ENCODING =
            Pattern.compile("(?i)transfer-encoding:.*?\\r\\ntransfer-encoding:");
    private static final Pattern DOUBLE_CONTENT_LENGTH =
            Pattern.compile("Content-Length: \\d+\\r\\nContent-Length: \\d+");
    private static final Pattern PIPELINE_CONNECTION_KEEPALIVE =
            Pattern.compile("Connection: keep-alive\\r\\nContent-Length: \\d+");
    private static final Pattern CACHE_HEADER_THEN_CONTENT_LENGTH =
            Pattern.compile("(?:Cache-Control|Vary|Expires): .*?\\r\\nContent-Length: \\d+");

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

    private HTTPHeaderValidationPipeline headerValuePipeline;
    private URLParameterValidationPipeline parameterValuePipeline;
    private SecurityEventCounter eventCounter;

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
        eventCounter = new SecurityEventCounter();
        headerValuePipeline = new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), eventCounter,
                ValidationType.HEADER_VALUE);
        parameterValuePipeline = new URLParameterValidationPipeline(
                SecurityConfiguration.builder().allowLineBreaksInParameterValues(false).build(), eventCounter);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("Every header-shaped smuggling attack is rejected for its raw line break")
    void shouldRejectAllHeaderShapedSmugglingAttacks(String smugglingAttack) {
        assertRejected(headerValuePipeline, smugglingAttack, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.QueryShaped.class, count = 200)
    @DisplayName("Every query-shaped smuggling attack is rejected for its decoded line break")
    void shouldRejectAllQueryShapedSmugglingAttacks(String smugglingAttack) {
        assertRejected(parameterValuePipeline, smugglingAttack, DECODED_LINE_BREAK_IN_PARAMETER_VALUE);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("CL.TE smuggling attacks must be blocked")
    void shouldBlockClTeSmuggling(String clTeAttack) {
        assertHeaderShapedFamilyRejected(clTeAttack, HttpRequestSmugglingAttackTest::isClTeFamily,
                CL_TE_SAMPLES, CL_TE_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("TE.CL smuggling attacks must be blocked")
    void shouldBlockTeClSmuggling(String teClAttack) {
        assertHeaderShapedFamilyRejected(teClAttack, HttpRequestSmugglingAttackTest::isTeClFamily,
                TE_CL_SAMPLES, TE_CL_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("TE.TE smuggling attacks must be blocked")
    void shouldBlockTeTeSmuggling(String teTeAttack) {
        assertHeaderShapedFamilyRejected(teTeAttack, HttpRequestSmugglingAttackTest::isTeTeFamily,
                TE_TE_SAMPLES, TE_TE_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("HTTP pipeline poisoning attacks must be blocked")
    void shouldBlockPipelinePoisoning(String pipelinePoisoningAttack) {
        assertHeaderShapedFamilyRejected(pipelinePoisoningAttack,
                HttpRequestSmugglingAttackTest::isPipelinePoisoningFamily, PIPELINE_SAMPLES, PIPELINE_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("Cache deception attacks must be blocked")
    void shouldBlockCacheDeception(String cacheDeceptionAttack) {
        assertHeaderShapedFamilyRejected(cacheDeceptionAttack,
                HttpRequestSmugglingAttackTest::isCacheDeceptionFamily, CACHE_SAMPLES, CACHE_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.HeaderShaped.class, count = 200)
    @DisplayName("Double Content-Length header attacks must be blocked")
    void shouldBlockDoubleContentLength(String doubleContentLengthAttack) {
        assertHeaderShapedFamilyRejected(doubleContentLengthAttack,
                HttpRequestSmugglingAttackTest::isDoubleContentLengthFamily, DOUBLE_CL_SAMPLES, DOUBLE_CL_ADMITTED);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 35)
    @DisplayName("Every smuggling family is rejected on the surface the generator shapes it for")
    void shouldRejectEverySmugglingFamilyOnItsOwnSurface(String smugglingAttack) {
        if (HttpRequestSmugglingAttackGenerator.surfaceOf(smugglingAttack) == Surface.HEADER_VALUE) {
            assertRejected(headerValuePipeline, smugglingAttack, RAW_LINE_BREAK_IN_HEADER_VALUE);
        } else {
            assertRejected(parameterValuePipeline, smugglingAttack, DECODED_LINE_BREAK_IN_PARAMETER_VALUE);
        }
    }

    /**
     * Counts the sample, drops it unless it belongs to the family under test, and otherwise
     * asserts the header-value verdict.
     */
    private void assertHeaderShapedFamilyRejected(String attack, Predicate<String> family,
            AtomicInteger samples, AtomicInteger admitted) {
        samples.incrementAndGet();
        if (!family.test(attack)) {
            return;
        }
        admitted.incrementAndGet();
        assertRejected(headerValuePipeline, attack, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    private void assertRejected(HttpSecurityValidator pipeline, String attack, UrlSecurityFailureType expected) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Request smuggling attack should be rejected: " + attack);

        assertEquals(expected, exception.getFailureType(), () -> "Unexpected verdict for: " + attack);
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }
}
