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
package de.cuioss.http.forwarded;

import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("ForwardedHeaderResolver")
@SuppressWarnings("java:S5778") // assertThrows lambdas intentionally wrap the whole failing call chain
class ForwardedHeaderResolverTest {

    private static Function<String, String> headers(Map<String, String> values) {
        return new HashMap<>(values)::get;
    }

    private static ForwardedHeaderResolver resolver(ForwardedResolverConfig config) {
        return new ForwardedHeaderResolver(config, new SecurityEventCounter());
    }

    private static ForwardedHeaderResolver trustAllResolver() {
        return resolver(ForwardedResolverConfig.builder().trustAll(true).build());
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNullArguments() {
            assertThrows(NullPointerException.class,
                    () -> new ForwardedHeaderResolver(null, new SecurityEventCounter()));
            assertThrows(NullPointerException.class,
                    () -> new ForwardedHeaderResolver(ForwardedResolverConfig.secureDefault(), null));
        }

        @Test
        @DisplayName("resolve rejects a null header lookup")
        void resolveRejectsNullLookup() {
            assertThrows(NullPointerException.class, () -> trustAllResolver().resolve(null));
        }
    }

    @Nested
    @DisplayName("Secure default")
    class SecureDefault {

        @Test
        @DisplayName("ignores every forwarded header")
        void ignoresEverything() {
            var lookup = headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "app.example.com",
                    "X-Forwarded-Port", "8443",
                    "X-Forwarded-Prefix", "/ui",
                    "X-Forwarded-For", "203.0.113.7"));

            assertEquals(ResolvedForwarding.empty(), resolver(ForwardedResolverConfig.secureDefault()).resolve(lookup));
        }

        @Test
        @DisplayName("resolves an un-proxied request to empty")
        void unproxiedRequestIsEmpty() {
            assertEquals(ResolvedForwarding.empty(), trustAllResolver().resolve(headers(Map.of())));
        }
    }

    @Nested
    @DisplayName("Scheme")
    class Scheme {

        @Test
        @DisplayName("honors X-Forwarded-Proto and lowercases it")
        void honorsXForwardedProto() {
            var result = trustAllResolver().resolve(headers(Map.of("X-Forwarded-Proto", "HTTPS")));
            assertEquals("https", result.scheme().orElseThrow());
        }

        @Test
        @DisplayName("falls back to X-ProxyScheme then RFC 7239 proto")
        void precedence() {
            assertEquals("http", trustAllResolver()
                    .resolve(headers(Map.of("X-ProxyScheme", "http"))).scheme().orElseThrow());
            assertEquals("https", trustAllResolver()
                    .resolve(headers(Map.of("Forwarded", "proto=https;host=x"))).scheme().orElseThrow());
        }

        @Test
        @DisplayName("takes the first token of a comma-separated list")
        void firstTokenOfList() {
            assertEquals("https", trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Proto", "https, http"))).scheme().orElseThrow());
        }

        @Test
        @DisplayName("drops a non-http(s) scheme")
        void dropsUnknownScheme() {
            assertTrue(trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Proto", "ftp"))).scheme().isEmpty());
        }

        @Test
        @DisplayName("is not honored without trustAll")
        void notHonoredWithoutTrustAll() {
            assertTrue(resolver(ForwardedResolverConfig.secureDefault())
                    .resolve(headers(Map.of("X-Forwarded-Proto", "https"))).scheme().isEmpty());
        }
    }

    @Nested
    @DisplayName("Host and port")
    class HostAndPort {

        @Test
        @DisplayName("honors a plain host")
        void plainHost() {
            var result = trustAllResolver().resolve(headers(Map.of("X-Forwarded-Host", "app.example.com")));
            assertEquals("app.example.com", result.host().orElseThrow());
            assertTrue(result.port().isEmpty());
        }

        @Test
        @DisplayName("splits host:port into host and port")
        void splitsHostPort() {
            var result = trustAllResolver().resolve(headers(Map.of("X-Forwarded-Host", "app.example.com:8443")));
            assertEquals("app.example.com", result.host().orElseThrow());
            assertEquals(8443, result.port().orElseThrow());
        }

        @Test
        @DisplayName("keeps bracketed IPv6 host and its port")
        void bracketedIpv6Host() {
            var result = trustAllResolver().resolve(headers(Map.of("X-Forwarded-Host", "[2001:db8::1]:8443")));
            assertEquals("[2001:db8::1]", result.host().orElseThrow());
            assertEquals(8443, result.port().orElseThrow());
        }

        @Test
        @DisplayName("X-Forwarded-Port overrides a port carried by the host")
        void explicitPortOverridesHostPort() {
            var result = trustAllResolver().resolve(headers(Map.of(
                    "X-Forwarded-Host", "app.example.com:8443",
                    "X-Forwarded-Port", "9000")));
            assertEquals(9000, result.port().orElseThrow());
        }

        @Test
        @DisplayName("falls back to X-ProxyHost and RFC 7239 host")
        void hostPrecedence() {
            assertEquals("proxy.example.com", trustAllResolver()
                    .resolve(headers(Map.of("X-ProxyHost", "proxy.example.com"))).host().orElseThrow());
            assertEquals("rfc.example.com", trustAllResolver()
                    .resolve(headers(Map.of("Forwarded", "host=rfc.example.com"))).host().orElseThrow());
        }

        @Test
        @DisplayName("drops an out-of-range or non-numeric port")
        void dropsInvalidPort() {
            assertTrue(trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Port", "70000"))).port().isEmpty());
            assertTrue(trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Port", "abc"))).port().isEmpty());
            assertTrue(trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Port", "0"))).port().isEmpty());
        }

        @Test
        @DisplayName("rejects a host carrying a path separator")
        void rejectsHostWithPath() {
            assertTrue(trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Host", "evil.com/path"))).host().isEmpty());
        }

        @Test
        @DisplayName("host and port are not honored without trustAll")
        void notHonoredWithoutTrustAll() {
            var result = resolver(ForwardedResolverConfig.secureDefault())
                    .resolve(headers(Map.of("X-Forwarded-Host", "app.example.com:8443")));
            assertTrue(result.host().isEmpty());
            assertTrue(result.port().isEmpty());
        }
    }

    @Nested
    @DisplayName("Context path")
    class ContextPath {

        @Test
        @DisplayName("honors and normalizes X-ProxyContextPath under trustAll")
        void honorsAndNormalizes() {
            assertEquals("/app", trustAllResolver()
                    .resolve(headers(Map.of("X-ProxyContextPath", "/app/"))).contextPath());
        }

        @Test
        @DisplayName("falls back to X-Forwarded-Prefix")
        void prefixFallback() {
            assertEquals("/ui", trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Prefix", "ui"))).contextPath());
        }

        @Test
        @DisplayName("honors only allowlisted paths when trustAll is false")
        void allowlistGated() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .allowedContextPaths(Set.of("/app"))
                    .build();

            assertEquals("/app", resolver(config)
                    .resolve(headers(Map.of("X-ProxyContextPath", "/app/"))).contextPath());
            assertEquals("", resolver(config)
                    .resolve(headers(Map.of("X-ProxyContextPath", "/attacker"))).contextPath());
        }

        @Test
        @DisplayName("rejects a protocol-relative prefix and warns")
        void rejectsProtocolRelative() {
            assertEquals("", trustAllResolver()
                    .resolve(headers(Map.of("X-ProxyContextPath", "//attacker.com"))).contextPath());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    "protocol-relative URL injection");
        }

        @Test
        @DisplayName("rejects a backslash-bearing prefix")
        void rejectsBackslash() {
            assertEquals("", trustAllResolver()
                    .resolve(headers(Map.of("X-ProxyContextPath", "/\\attacker.com"))).contextPath());
        }

        @Test
        @DisplayName("a bare Forwarded header yields no context path")
        void forwardedHasNoPrefix() {
            assertEquals("", trustAllResolver()
                    .resolve(headers(Map.of("Forwarded", "proto=https;host=x"))).contextPath());
        }
    }

    @Nested
    @DisplayName("Client IP")
    class ClientIp {

        private ForwardedResolverConfig trustingProxies(String... cidrs) {
            return ForwardedResolverConfig.builder()
                    .trustAll(true)
                    .trustedProxies(Set.of(cidrs))
                    .build();
        }

        @Test
        @DisplayName("resolves the first untrusted hop from the right")
        void resolvesFirstUntrusted() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, 10.0.0.5")));
            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("ignores a spoofed prepended entry")
        void ignoresSpoofedEntry() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "1.2.3.4, 203.0.113.7, 10.0.0.5")));
            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("returns empty when every hop is trusted")
        void allTrustedIsEmpty() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "10.0.0.1, 10.0.0.5")));
            assertTrue(result.clientIp().isEmpty());
        }

        @Test
        @DisplayName("returns empty (and warns) on an unparseable rightmost hop")
        void unparseableHopIsEmpty() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, garbage")));
            assertTrue(result.clientIp().isEmpty());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "unparseable entry");
        }

        @Test
        @DisplayName("resolves a bare IPv6 client to its canonical form")
        void ipv6Client() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "2001:db8::1, 10.0.0.5")));
            assertEquals("2001:db8:0:0:0:0:0:1", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("handles IPv6 CIDR trusted proxies")
        void ipv6TrustedProxy() {
            var result = resolver(trustingProxies("2001:db8::/32"))
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7, 2001:db8::5")));
            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("uses the RFC 7239 for chain when X-Forwarded-For is absent")
        void rfcForChain() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("Forwarded", "for=203.0.113.7, for=\"10.0.0.5\"")));
            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("strips a port from a for entry")
        void stripsPort() {
            var result = resolver(trustingProxies("10.0.0.0/8"))
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7:51234, 10.0.0.5")));
            assertEquals("203.0.113.7", result.clientIp().orElseThrow());
        }

        @Test
        @DisplayName("is not honored when no trusted proxies are configured")
        void noTrustedProxiesIsEmpty() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-For", "203.0.113.7")));
            assertTrue(result.clientIp().isEmpty(),
                    "Without trusted proxies the chain is not honored even under trustAll");
        }
    }

    @Nested
    @DisplayName("Fail-safe sanitization")
    class FailSafe {

        @Test
        @DisplayName("drops a CRLF-injecting header value without throwing and warns")
        void dropsCrlfInjection() {
            var result = trustAllResolver()
                    .resolve(headers(Map.of("X-Forwarded-Host", "evil.com\r\nInjected: 1")));
            assertTrue(result.host().isEmpty());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "failed security sanitization");
        }
    }

    @Nested
    @DisplayName("Round trip")
    class RoundTrip {

        @Test
        @DisplayName("re-resolves X-Forwarded-* serialization to the same result")
        void xForwardedRoundTrip() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .trustAll(true)
                    .trustedProxies(Set.of("10.0.0.0/8"))
                    .build();
            ForwardedHeaderResolver resolver = resolver(config);

            var original = resolver.resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "app.example.com:8443",
                    "X-Forwarded-Prefix", "/ui/",
                    "X-Forwarded-For", "203.0.113.7, 10.0.0.5")));

            assertEquals("https", original.scheme().orElseThrow());
            assertEquals("app.example.com", original.host().orElseThrow());
            assertEquals(8443, original.port().orElseThrow());
            assertEquals("/ui", original.contextPath());
            assertEquals("203.0.113.7", original.clientIp().orElseThrow());

            var reResolved = resolver.resolve(headers(original.toXForwardedHeaders()));
            assertEquals(original, reResolved);
        }

        @Test
        @DisplayName("re-resolves the RFC 7239 Forwarded serialization (context path excepted)")
        void forwardedRoundTrip() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .trustAll(true)
                    .trustedProxies(Set.of("10.0.0.0/8"))
                    .build();
            ForwardedHeaderResolver resolver = resolver(config);

            var original = resolver.resolve(headers(Map.of(
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "app.example.com:8443",
                    "X-Forwarded-For", "203.0.113.7, 10.0.0.5")));

            String forwarded = original.toForwardedHeader().orElseThrow();
            var reResolved = resolver.resolve(headers(Map.of("Forwarded", forwarded)));

            assertEquals(original.scheme(), reResolved.scheme());
            assertEquals(original.host(), reResolved.host());
            assertEquals(original.port(), reResolved.port());
            assertEquals(original.clientIp(), reResolved.clientIp());
            assertEquals("", reResolved.contextPath());
        }

        @Test
        @DisplayName("context path is lost through Forwarded but kept through X-Forwarded-Prefix")
        void contextPathAsymmetry() {
            var forwarding = new ResolvedForwarding(Optional.empty(), Optional.empty(),
                    OptionalInt.empty(), "/ui", Optional.empty());
            assertTrue(forwarding.toForwardedHeader().isEmpty());
            assertFalse(forwarding.toXForwardedHeaders().isEmpty());
        }
    }
}
