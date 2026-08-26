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
package de.cuioss.http.forwarded;

import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression suite for the append-style proxy attack shapes against
 * {@link ForwardedHeaderResolver}.
 *
 * <p>Every hop in a forwarded chain <em>appends</em>. An attacker who controls the original client
 * therefore contributes the <em>leading</em> token (or the first repeated header line), and the
 * trusted proxy contributes the trailing one. Each group below drives one attack shape and asserts
 * that the proxy-attested value wins:</p>
 * <ul>
 *   <li><strong>FW-3</strong> — append-style scheme / host / port, in both the de-facto
 *       {@code X-Forwarded-*} family and the RFC 7239 {@code Forwarded} header.</li>
 *   <li><strong>FW-6</strong> — append-style {@code X-Forwarded-Prefix}, including the
 *       protocol-relative and whitespace injection guards that must run against the selected
 *       nearest-hop token rather than the whole raw value.</li>
 *   <li><strong>FW-2</strong> — the fail-closed source-reconciliation guard: a disagreement between
 *       the de-facto family and RFC 7239 drops the field, and agreeing / single-source inputs are
 *       left untouched.</li>
 *   <li><strong>FW-1</strong> — the same gaps re-run with the attacker's value on a <em>separate
 *       header instance</em>, proving that the wire-order join and the nearest-hop token selection
 *       compose end-to-end. This is the counterpart to {@link ForwardedAccessorContractTest}, which
 *       pins the caller-side accessor obligation in isolation.</li>
 *   <li><strong>Fail-closed chain walk</strong> — the all-hops-trusted and unparseable-hop
 *       behaviours still yield no client IP, guarding against a regression that opens the chain walk
 *       while the other gaps are closed.</li>
 * </ul>
 *
 * <p>Every attack-shape test carries a matched negative control (a benign, agreeing or single-source
 * input that must still resolve), so a blanket "always empty" regression cannot pass this suite. No
 * test depends on DNS resolution, wall-clock time, or ordering between tests.</p>
 */
@EnableTestLogger
@DisplayName("Forwarded trust boundary")
class ForwardedTrustBoundaryTest {

    private static final String PROXY_HOST = "app.example.com";
    private static final String ATTACKER_HOST = "attacker.example";
    private static final String TRUSTED_PROXY_RANGE = "10.0.0.0/8";
    private static final String SOURCES_DISAGREE = "sources disagree";

    /** Single-instance accessor: each header is present exactly once, as one comma-joined value. */
    private static Function<String, List<String>> headers(Map<String, String> values) {
        Map<String, String> copy = new HashMap<>(values);
        return name -> {
            String value = copy.get(name);
            return value == null ? null : List.of(value);
        };
    }

    /** Multi-instance accessor: exposes every repeated header line, in wire order. */
    private static Function<String, List<String>> repeatedHeaders(Map<String, List<String>> values) {
        return new HashMap<>(values)::get;
    }

    private static ForwardedHeaderResolver resolver(ForwardedResolverConfig config) {
        return new ForwardedHeaderResolver(config, new SecurityEventCounter());
    }

    private static ForwardedHeaderResolver trustAllResolver() {
        return resolver(ForwardedResolverConfig.builder().trustAll(true).build());
    }

    private static ForwardedHeaderResolver chainWalkingResolver() {
        return resolver(ForwardedResolverConfig.builder()
                .trustAll(true)
                .trustedProxies(Set.of(TRUSTED_PROXY_RANGE))
                .build());
    }

    @Nested
    @DisplayName("FW-3 append-style scheme, host and port")
    class AppendStyleSchemeHostPort {

        @Test
        @DisplayName("the X-Forwarded-* family resolves the proxy's appended token, not the attacker's leading one")
        void xForwardedFamilyResolvesAppendedToken() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https, http",
                    "X-Forwarded-Host", ATTACKER_HOST + ", " + PROXY_HOST,
                    "X-Forwarded-Port", "8443, 9000")));

            assertAll("the nearest hop appends last, so its token wins for every field",
                    () -> assertEquals("http", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()),
                    () -> assertEquals(9000, result.port().orElseThrow()));
        }

        @Test
        @DisplayName("RFC 7239 resolves the proxy's appended element, not the attacker's leading one")
        void rfc7239ResolvesAppendedElement() {
            var result = trustAllResolver().resolve(headers(Map.of("Forwarded",
                    "proto=https;host=" + ATTACKER_HOST + ":8443, proto=http;host=" + PROXY_HOST + ":9000")));

            assertAll("the last proto/host directive is the nearest hop's, and its port carries through",
                    () -> assertEquals("http", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()),
                    () -> assertEquals(9000, result.port().orElseThrow()));
        }

        @Test
        @DisplayName("negative control: a benign single-token value is honored unchanged")
        void benignSingleTokenHonored() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "http",
                    "X-Forwarded-Host", PROXY_HOST,
                    "X-Forwarded-Port", "9000")));

            assertAll("without a prepended token there is nothing to strip",
                    () -> assertEquals("http", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()),
                    () -> assertEquals(9000, result.port().orElseThrow()));
        }
    }

    @Nested
    @DisplayName("FW-6 append-style context-path prefix")
    class AppendStylePrefix {

        @Test
        @DisplayName("resolves the proxy's appended prefix, not the attacker's leading one")
        void appendedPrefixWins() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Prefix", "/app, /other")));

            assertEquals("/other", result.contextPath(),
                    "the nearest hop appends last, so its prefix wins");
        }

        @Test
        @DisplayName("rejects an appended protocol-relative prefix and warns")
        void appendedProtocolRelativePrefixRejected() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Prefix", "/app, //attacker.com")));

            assertEquals("", result.contextPath(),
                    "the guard must run against the selected token, not the whole raw value");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    "protocol-relative URL injection");
        }

        @Test
        @DisplayName("rejects an appended prefix carrying whitespace")
        void appendedWhitespacePrefixRejected() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Prefix", "/app, /oth er")));

            assertEquals("", result.contextPath(),
                    "a well-formed context path carries no whitespace");
        }

        @Test
        @DisplayName("negative control: a benign single prefix is honored")
        void benignSinglePrefixHonored() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Prefix", "/app")));

            assertEquals("/app", result.contextPath());
        }
    }

    @Nested
    @DisplayName("FW-2 source disagreement")
    class SourceDisagreement {

        @Test
        @DisplayName("drops the scheme and warns when RFC 7239 downgrades what X-Forwarded-Proto attests")
        void downgradeAcrossSourcesDropsScheme() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "Forwarded", "proto=http")));

            assertTrue(result.scheme().isEmpty(),
                    "a forged source must not win by precedence, in either direction");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SOURCES_DISAGREE);
        }

        @Test
        @DisplayName("negative control: agreeing sources are honored")
        void agreeingSourcesHonored() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "Forwarded", "proto=https")));

            assertEquals("https", result.scheme().orElseThrow(),
                    "the guard must fire on disagreement only");
        }

        @Test
        @DisplayName("negative control: a single present source is honored from either family")
        void singleSourceHonored() {
            var fromDeFacto = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Proto", "https")));
            var fromRfc = trustAllResolver()
                    .resolve(headers(Map.of("Forwarded", "proto=http")));

            assertAll("one present source cannot disagree with an absent one",
                    () -> assertEquals("https", fromDeFacto.scheme().orElseThrow()),
                    () -> assertEquals("http", fromRfc.scheme().orElseThrow()));
        }
    }

    @Nested
    @DisplayName("FW-1 repeated header lines")
    class RepeatedHeaderLines {

        @Test
        @DisplayName("resolves the proxy's appended header instance for scheme, host and port")
        void repeatedSchemeHostPortResolveAppendedInstance() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Proto", List.of("https", "http"),
                    "X-Forwarded-Host", List.of(ATTACKER_HOST, PROXY_HOST),
                    "X-Forwarded-Port", List.of("8443", "9000"))));

            assertAll("the wire-order join and the nearest-hop selection compose",
                    () -> assertEquals("http", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()),
                    () -> assertEquals(9000, result.port().orElseThrow()));
        }

        @Test
        @DisplayName("resolves the proxy's appended prefix instance")
        void repeatedPrefixResolvesAppendedInstance() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Prefix", List.of("/app", "/other"))));

            assertEquals("/other", result.contextPath());
        }

        @Test
        @DisplayName("rejects a protocol-relative prefix carried on an appended instance and warns")
        void repeatedProtocolRelativePrefixRejected() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Prefix", List.of("/app", "//attacker.com"))));

            assertEquals("", result.contextPath());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    "protocol-relative URL injection");
        }

        @Test
        @DisplayName("repeated Forwarded instances reconcile with an agreeing X-Forwarded-Proto")
        void repeatedForwardedInstancesAgreeWithDeFactoFamily() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Proto", List.of("http"),
                    "Forwarded", List.of("proto=https", "proto=http"))));

            assertEquals("http", result.scheme().orElseThrow(),
                    "joining in wire order and taking the last directive is what makes the sources agree");
        }

        @Test
        @DisplayName("drops the scheme when an appended Forwarded instance contradicts X-Forwarded-Proto")
        void repeatedForwardedInstanceDisagreeingDropsScheme() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Proto", List.of("http"),
                    "Forwarded", List.of("proto=http", "proto=https"))));

            assertTrue(result.scheme().isEmpty(),
                    "the nearest-hop directive disagrees with the de-facto family, so the field is dropped");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SOURCES_DISAGREE);
        }

        @Test
        @DisplayName("negative control: a single header instance is honored verbatim")
        void singleInstanceHonored() {
            var result = trustAllResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-Proto", List.of("https"),
                    "X-Forwarded-Host", List.of(PROXY_HOST),
                    "X-Forwarded-Prefix", List.of("/app"))));

            assertAll("with no appended instance there is nothing to prefer",
                    () -> assertEquals("https", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()),
                    () -> assertEquals("/app", result.contextPath()));
        }
    }

    @Nested
    @DisplayName("Fail-closed chain walk")
    class FailClosedChainWalk {

        @Test
        @DisplayName("yields no client IP when every appended hop is a trusted proxy")
        void everyHopTrustedYieldsNoClientIp() {
            var result = chainWalkingResolver().resolve(repeatedHeaders(Map.of(
                    "X-Forwarded-For", List.of("10.0.0.1", "10.0.0.5"))));

            assertTrue(result.clientIp().isEmpty(),
                    "a chain of only trusted proxies identifies no originating client");
        }

        @Test
        @DisplayName("yields no client IP and warns when the nearest hop is unparseable")
        void unparseableNearestHopYieldsNoClientIp() {
            var result = chainWalkingResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, garbage")));

            assertTrue(result.clientIp().isEmpty(),
                    "an unverifiable chain must not fall back to an earlier, attacker-supplied hop");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "unparseable entry");
        }

        @Test
        @DisplayName("positive control: the first untrusted hop from the right is resolved")
        void untrustedHopResolves() {
            var result = chainWalkingResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "6.6.6.6, 203.0.113.7, 10.0.0.5")));

            assertEquals("203.0.113.7", result.clientIp().orElseThrow(),
                    "the chain walk is still open for a genuinely untrusted hop");
        }
    }
}
