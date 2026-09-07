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
 *   <li><strong>Parser strictness cluster</strong> — the tightened parser guards, each driven
 *       end-to-end through the public resolver so the assertion captures the user-visible
 *       reject outcome: a malformed RFC 7239 {@code forwarded-pair}, a bracketed chain hop with
 *       trailing garbage, a leading-zero IPv4 octet, an unbracketed IPv6 host, and a non-digit
 *       port. The symmetric {@code X-Forwarded-Host: [::1]garbage} guard is driven through the
 *       same public surface by {@link ForwardedHeaderResolverTest}, so it is not duplicated
 *       here.</li>
 *   <li><strong>FW-7</strong> — the corrected trust model as a whole: a garbage {@code Forwarded}
 *       header suppresses only the fields it actually spoke about, the two de-facto families are
 *       reconciled by the configured tie-breaker, {@code trustedProxies} gates whether forwarded
 *       headers are believed at all, and host and port are reconciled as independent fields.</li>
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

    @Nested
    @DisplayName("Parser strictness cluster")
    class ParserStrictness {

        @Test
        @DisplayName("a malformed Forwarded directive drops only the fields it reached before breaking")
        void malformedForwardedDropsDeFactoFields() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", PROXY_HOST,
                    "Forwarded", "proto=;host=" + PROXY_HOST)));

            assertAll("the parse broke on the very first pair, so this header spoke about no field at all",
                    () -> assertEquals("https", result.scheme().orElseThrow(),
                            "proto= carries no value, so no proto directive was ever accumulated"),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow(),
                            "the host directive sits after the stop and was never read"));
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "malformed forwarded-pair");
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, SOURCES_DISAGREE);
        }

        @Test
        @DisplayName("the same malformed header drops the field it did reach, when order puts that field first")
        void malformedForwardedDropsTheFieldItReached() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", PROXY_HOST,
                    "Forwarded", "host=" + PROXY_HOST + ";proto=")));

            assertAll("swapping the order moves the boundary, and nothing else",
                    () -> assertEquals("https", result.scheme().orElseThrow(),
                            "the broken proto= pair IS the stop, so no proto directive is accumulated from it"),
                    () -> assertTrue(result.host().isEmpty(),
                            "host was read before the stop, so the field fails closed even though the values match"));
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "malformed forwarded-pair");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SOURCES_DISAGREE);
        }

        @Test
        @DisplayName("positive control: a well-formed Forwarded header agreeing with the de-facto family is honored")
        void wellFormedForwardedHonored() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", PROXY_HOST,
                    "Forwarded", "proto=https;host=" + PROXY_HOST)));

            assertAll("a legal header must still reconcile",
                    () -> assertEquals("https", result.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow()));
        }

        @Test
        @DisplayName("a bracketed hop with trailing garbage aborts the chain walk rather than being skipped")
        void bracketTrailingGarbageAbortsChainWalk() {
            var result = chainWalkingResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, [::1]garbage")));

            assertTrue(result.clientIp().isEmpty(),
                    "203.0.113.7 is untrusted and would resolve if the bad hop were merely skipped");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "unparseable entry");
        }

        @Test
        @DisplayName("a leading-zero IPv4 octet in the chain aborts the walk")
        void leadingZeroOctetAbortsChainWalk() {
            var result = chainWalkingResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "010.0.0.5, 10.0.0.5")));

            assertTrue(result.clientIp().isEmpty(),
                    "an octal-ambiguous octet is unverifiable, so the chain yields no client");
        }

        @Test
        @DisplayName("positive control: the same chain shape resolves with well-formed literals")
        void wellFormedChainResolves() {
            var result = chainWalkingResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, 10.0.0.5")));

            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("an unbracketed IPv6 X-Forwarded-Host yields no host")
        void unbracketedIpv6HostDropped() {
            assertTrue(trustAllResolver()
                            .resolve(headers(Map.of("X-Forwarded-Host", "2001:db8::1"))).host().isEmpty(),
                    "an unbracketed IPv6 literal would compose into a malformed URL authority");
        }

        @Test
        @DisplayName("positive control: the bracketed form of the same host is honored")
        void bracketedIpv6HostHonored() {
            assertEquals("[2001:db8::1]", trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Host", "[2001:db8::1]"))).host().orElseThrow());
        }

        @Test
        @DisplayName("a non-digit X-Forwarded-Port yields no port and does not fall back to the host port")
        void nonDigitPortDoesNotFallBack() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST + ":8443",
                    "X-Forwarded-Port", "+443")));

            assertAll("a present-but-invalid port header drops the field outright",
                    () -> assertTrue(result.port().isEmpty()),
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow(),
                            "only the port is dropped, not the host"));
        }

        @Test
        @DisplayName("positive control: a digit-only X-Forwarded-Port overrides the host port")
        void digitOnlyPortHonored() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST + ":8443",
                    "X-Forwarded-Port", "443")));

            assertEquals(443, result.port().orElseThrow());
        }
    }

    /**
     * The corrected trust model, driven end to end from a request-level angle. Each case pins one
     * production correction and fails when that correction alone is reverted: a garbage
     * {@code Forwarded} header no longer erases what a legitimate de-facto header attested, the two
     * de-facto families are reconciled by the configured tie-breaker rather than by header order,
     * {@code trustedProxies} decides whether forwarded headers are believed at all, and host and
     * port are compared as independent fields.
     */
    @Nested
    @DisplayName("FW-7 corrected trust model")
    class CorrectedTrustModel {

        private static final String TRUSTED_PEER = "10.0.0.5";
        private static final String OUTSIDE_PEER = "203.0.113.9";

        /** One proxy-attested header set, resolved either fully or not at all depending on the peer. */
        private final Function<String, List<String>> proxyAttestedHeaders = headers(Map.of(
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", PROXY_HOST,
                "X-Forwarded-For", "203.0.113.7, 10.0.0.5"));

        private ForwardedHeaderResolver preferringXProxy() {
            return resolver(ForwardedResolverConfig.builder()
                    .trustAll(true)
                    .deFactoPrecedence(ForwardedResolverConfig.DeFactoFamily.X_PROXY)
                    .build());
        }

        @Test
        @DisplayName("a client-supplied garbage Forwarded header leaves the ingress's X-Forwarded-Proto standing")
        void garbageForwardedLeavesLegitimateSchemeStanding() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "Forwarded", "garbage")));

            assertEquals("https", result.scheme().orElseThrow(),
                    "the garbage header reached no proto directive, so it spoke about no field and "
                            + "must not suppress one the ingress did attest");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "malformed forwarded-pair");
        }

        @Test
        @DisplayName("negative control: garbage appended AFTER a proto directive still drops the scheme")
        void garbageAfterAProtoDirectiveStillDropsScheme() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "Forwarded", "proto=http;garbage")));

            assertTrue(result.scheme().isEmpty(),
                    "the parser reached the proto directive before it stopped, so this header DID "
                            + "speak about the scheme and the field fails closed");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SOURCES_DISAGREE);
        }

        @Test
        @DisplayName("an attacker-supplied X-Forwarded-Host loses to the X-ProxyHost the precedence knob names")
        void configuredDeFactoFamilyWinsTheHost() {
            var result = preferringXProxy().resolve(headers(Map.of(
                    "X-Forwarded-Host", ATTACKER_HOST,
                    "X-ProxyHost", PROXY_HOST)));

            assertEquals(PROXY_HOST, result.host().orElseThrow(),
                    "a client-supplied X-Forwarded-Host must not override the family this ingress writes");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "de-facto families disagree");
        }

        @Test
        @DisplayName("negative control: agreeing de-facto families resolve quietly")
        void agreeingDeFactoFamiliesResolveQuietly() {
            var result = preferringXProxy().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST,
                    "X-ProxyHost", PROXY_HOST)));

            assertEquals(PROXY_HOST, result.host().orElseThrow());
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, ForwardedHeaderResolver.class);
        }

        @Test
        @DisplayName("an untrusted peer resolves nothing for the very header set a peerless request resolves fully")
        void untrustedPeerAttestsNothing() {
            var forwardedResolver = chainWalkingResolver();
            var viaOutsidePeer =
                    forwardedResolver.resolve(proxyAttestedHeaders, IpAddresses.parse(OUTSIDE_PEER));
            var withoutPeer = forwardedResolver.resolve(proxyAttestedHeaders);

            assertAll("the same headers attest everything or nothing, decided by the peer alone",
                    () -> assertEquals(ResolvedForwarding.empty(), viaOutsidePeer,
                            "a request that did not arrive through the proxy tier attests nothing"),
                    () -> assertEquals("https", withoutPeer.scheme().orElseThrow()),
                    () -> assertEquals(PROXY_HOST, withoutPeer.host().orElseThrow()),
                    () -> assertEquals("203.0.113.7", withoutPeer.clientIp().orElseThrow()));
        }

        @Test
        @DisplayName("positive control: a trusted peer resolves exactly what the peerless overload does")
        void trustedPeerResolvesFully() {
            var forwardedResolver = chainWalkingResolver();

            assertEquals(forwardedResolver.resolve(proxyAttestedHeaders),
                    forwardedResolver.resolve(proxyAttestedHeaders, IpAddresses.parse(TRUSTED_PEER)),
                    "the peer gate admits the request, it does not re-resolve it");
        }

        @Test
        @DisplayName("a host and port split across X-Forwarded-Port and a Forwarded host directive resolves both")
        void hostAndPortSplitAcrossSourcesResolves() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST,
                    "X-Forwarded-Port", "8443",
                    "Forwarded", "host=\"" + PROXY_HOST + ":8443\"")));

            assertAll("host and port are compared as independent fields",
                    () -> assertEquals(PROXY_HOST, result.host().orElseThrow(),
                            "both sources name the same host; comparing the pair as one record "
                                    + "made the differing port placement look like a conflict"),
                    () -> assertEquals(8443, result.port().orElseThrow()));
        }

        @Test
        @DisplayName("negative control: a real conflict still fails closed, and takes only the field that conflicted")
        void conflictDropsOnlyTheConflictingField() {
            var forgedHost = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST,
                    "X-Forwarded-Port", "8443",
                    "Forwarded", "host=\"" + ATTACKER_HOST + ":8443\"")));
            var forgedPort = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", PROXY_HOST + ":8443",
                    "Forwarded", "host=\"" + PROXY_HOST + ":9000\"")));

            assertAll("splitting the comparison must not weaken the fail-closed rule in either direction",
                    () -> assertTrue(forgedHost.host().isEmpty(),
                            "the sources name different hosts, so no host may be honored"),
                    () -> assertEquals(8443, forgedHost.port().orElseThrow(),
                            "the port was never in dispute, so the host conflict must not erase it"),
                    () -> assertEquals(PROXY_HOST, forgedPort.host().orElseThrow(),
                            "the sources name the same host, so the conflicting port must not erase it"),
                    () -> assertTrue(forgedPort.port().isEmpty(),
                            "8443 against 9000 is a real conflict, so no port may be honored"));
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SOURCES_DISAGREE);
        }
    }
}
