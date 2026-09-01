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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.handler.RedirectPolicy.CredentialForwarding;
import de.cuioss.http.client.handler.RedirectPolicy.RedirectRefusal;
import de.cuioss.http.client.result.HttpErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RedirectPolicy} and {@link RedirectNotAllowedException}.
 */
class RedirectPolicyTest {

    private static final URI ORIGIN = URI.create("https://api.example.com/v1/resource");

    private static void assertPermitted(RedirectPolicy policy, URI from, URI to) {
        Optional<RedirectRefusal> refusal = policy.refuse(from, to);
        assertTrue(refusal.isEmpty(), () -> "expected hop " + from + " -> " + to + " to be permitted, but was refused with " + refusal.orElse(null));
    }

    private static void assertRefused(RedirectPolicy policy, URI from, URI to, RedirectRefusal expected) {
        Optional<RedirectRefusal> refusal = policy.refuse(from, to);
        assertTrue(refusal.isPresent(), () -> "expected hop " + from + " -> " + to + " to be refused with " + expected + ", but it was permitted");
        assertEquals(expected, refusal.get());
    }

    @Nested
    @DisplayName("Defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Should default sameOrigin() to 10 hops, no allowed hosts and STRIP_ON_CROSS_ORIGIN")
        void shouldExposeSecureDefaults() {
            RedirectPolicy policy = RedirectPolicy.sameOrigin();

            assertEquals(10, policy.getMaxHops());
            assertEquals(RedirectPolicy.DEFAULT_MAX_HOPS, policy.getMaxHops());
            assertTrue(policy.getAllowedHosts().isEmpty());
            assertEquals(CredentialForwarding.STRIP_ON_CROSS_ORIGIN, policy.getCredentialForwarding());
        }

        @Test
        @DisplayName("Should default an unnamed builder to the same secure defaults as sameOrigin()")
        void shouldDefaultUnnamedBuilderToSameOrigin() {
            RedirectPolicy built = RedirectPolicy.builder().build();

            assertEquals(RedirectPolicy.sameOrigin(), built);
            assertEquals(CredentialForwarding.STRIP_ON_CROSS_ORIGIN, built.getCredentialForwarding());
        }
    }

    @Nested
    @DisplayName("refuse — rule 1: unsupported scheme")
    class UnsupportedSchemeTests {

        @ParameterizedTest
        @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/pub", "gopher://api.example.com/1"})
        @DisplayName("Should refuse a non-http(s) target with UNSUPPORTED_SCHEME")
        void shouldRefuseNonHttpSchemes(String target) {
            assertRefused(RedirectPolicy.sameOrigin(), ORIGIN, URI.create(target), RedirectRefusal.UNSUPPORTED_SCHEME);
        }

        @Test
        @DisplayName("Should refuse a non-http(s) target with UNSUPPORTED_SCHEME even when its host is allowlisted")
        void shouldRefuseNonHttpSchemeAheadOfAllowlist() {
            RedirectPolicy policy = RedirectPolicy.builder()
                    .allowedHosts(List.of("api.example.com"))
                    .build();

            assertRefused(policy, ORIGIN, URI.create("ftp://api.example.com/pub"), RedirectRefusal.UNSUPPORTED_SCHEME);
        }

        @Test
        @DisplayName("Should permit an http(s) target — the positive control for rule 1")
        void shouldPermitHttpsTarget() {
            assertPermitted(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("https://api.example.com/v2/other"));
        }
    }

    @Nested
    @DisplayName("refuse — rule 2: protocol downgrade")
    class ProtocolDowngradeTests {

        @Test
        @DisplayName("Should refuse an https to http hop with PROTOCOL_DOWNGRADE")
        void shouldRefuseDowngrade() {
            assertRefused(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("http://api.example.com/v1/resource"),
                    RedirectRefusal.PROTOCOL_DOWNGRADE);
        }

        @Test
        @DisplayName("Should refuse an https to http hop with PROTOCOL_DOWNGRADE even when the target host is allowlisted")
        void shouldRefuseDowngradeAheadOfAllowlist() {
            RedirectPolicy policy = RedirectPolicy.builder()
                    .allowedHosts(List.of("cdn.example.net"))
                    .build();

            assertRefused(policy, ORIGIN, URI.create("http://cdn.example.net/asset"), RedirectRefusal.PROTOCOL_DOWNGRADE);
        }

        @Test
        @DisplayName("Should not treat an http to http hop as a downgrade")
        void shouldPermitCleartextToCleartextSameOrigin() {
            URI cleartextOrigin = URI.create("http://api.example.com/v1/resource");

            assertPermitted(RedirectPolicy.sameOrigin(), cleartextOrigin, URI.create("http://api.example.com/v2/other"));
        }

        @Test
        @DisplayName("Should refuse an http to https cross-scheme hop with CROSS_ORIGIN, not PROTOCOL_DOWNGRADE")
        void shouldRefuseUpgradeAsCrossOrigin() {
            URI cleartextOrigin = URI.create("http://api.example.com/v1/resource");

            assertRefused(RedirectPolicy.sameOrigin(), cleartextOrigin, URI.create("https://api.example.com/v1/resource"),
                    RedirectRefusal.CROSS_ORIGIN);
        }
    }

    @Nested
    @DisplayName("refuse — rule 3: same origin")
    class SameOriginTests {

        @Test
        @DisplayName("Should permit a same-origin hop to a different path")
        void shouldPermitSameOrigin() {
            assertPermitted(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("https://api.example.com/v2/other"));
        }

        @Test
        @DisplayName("Should treat an absent https port and an explicit 443 as the same origin")
        void shouldNormaliseDefaultHttpsPort() {
            assertPermitted(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("https://api.example.com:443/v2/other"));
            assertPermitted(RedirectPolicy.sameOrigin(), URI.create("https://api.example.com:443/v1"),
                    URI.create("https://api.example.com/v2"));
        }

        @Test
        @DisplayName("Should treat an absent http port and an explicit 80 as the same origin")
        void shouldNormaliseDefaultHttpPort() {
            assertPermitted(RedirectPolicy.sameOrigin(), URI.create("http://api.example.com/v1"),
                    URI.create("http://api.example.com:80/v2"));
        }

        @Test
        @DisplayName("Should compare host case-insensitively")
        void shouldCompareHostCaseInsensitively() {
            assertPermitted(RedirectPolicy.sameOrigin(), URI.create("https://API.EXAMPLE.COM/v1"),
                    URI.create("https://api.example.com/v2"));
        }

        @Test
        @DisplayName("Should compare scheme case-insensitively")
        void shouldCompareSchemeCaseInsensitively() {
            assertPermitted(RedirectPolicy.sameOrigin(), URI.create("HTTPS://api.example.com/v1"),
                    URI.create("https://api.example.com/v2"));
        }

        @Test
        @DisplayName("Should refuse a hop to a different port with CROSS_ORIGIN")
        void shouldRefuseCrossPort() {
            assertRefused(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("https://api.example.com:8443/v1/resource"),
                    RedirectRefusal.CROSS_ORIGIN);
        }

        @Test
        @DisplayName("Should refuse a hop to a different host with CROSS_ORIGIN")
        void shouldRefuseCrossHost() {
            assertRefused(RedirectPolicy.sameOrigin(), ORIGIN, URI.create("https://cdn.example.net/asset"),
                    RedirectRefusal.CROSS_ORIGIN);
        }
    }

    @Nested
    @DisplayName("refuse — rule 4: allowlist")
    class AllowlistTests {

        @Test
        @DisplayName("Should permit a cross-host hop to an allowlisted host")
        void shouldPermitAllowlistedCrossHost() {
            RedirectPolicy policy = RedirectPolicy.builder()
                    .allowedHosts(List.of("cdn.example.net"))
                    .build();

            assertPermitted(policy, ORIGIN, URI.create("https://cdn.example.net/asset"));
        }

        @Test
        @DisplayName("Should refuse a cross-host hop to a host outside the allowlist with CROSS_ORIGIN")
        void shouldRefuseNonAllowlistedCrossHost() {
            RedirectPolicy policy = RedirectPolicy.builder()
                    .allowedHosts(List.of("cdn.example.net"))
                    .build();

            assertRefused(policy, ORIGIN, URI.create("https://evil.example.org/asset"), RedirectRefusal.CROSS_ORIGIN);
        }

        @Test
        @DisplayName("Should match the allowlist case-insensitively on both the configured and the target host")
        void shouldMatchAllowlistCaseInsensitively() {
            RedirectPolicy policy = RedirectPolicy.builder()
                    .allowedHosts(List.of("CDN.Example.NET"))
                    .build();

            assertEquals(Set.of("cdn.example.net"), policy.getAllowedHosts());
            assertPermitted(policy, ORIGIN, URI.create("https://CDN.EXAMPLE.net/asset"));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("Should reject a non-positive maxHops")
        void shouldRejectNonPositiveMaxHops(int maxHops) {
            RedirectPolicy.RedirectPolicyBuilder builder = RedirectPolicy.builder();

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> builder.maxHops(maxHops));
            assertTrue(thrown.getMessage().contains("maxHops"));
        }

        @Test
        @DisplayName("Should accept a positive maxHops")
        void shouldAcceptPositiveMaxHops() {
            assertEquals(3, RedirectPolicy.builder().maxHops(3).build().getMaxHops());
        }

        @Test
        @DisplayName("Should defensively copy allowedHosts so later mutation of the source does not leak in")
        void shouldDefensivelyCopyAllowedHosts() {
            List<String> source = new ArrayList<>();
            source.add("cdn.example.net");
            RedirectPolicy policy = RedirectPolicy.builder().allowedHosts(source).build();

            source.add("evil.example.org");

            assertEquals(Set.of("cdn.example.net"), policy.getAllowedHosts());
            assertRefused(policy, ORIGIN, URI.create("https://evil.example.org/asset"), RedirectRefusal.CROSS_ORIGIN);
        }

        @Test
        @DisplayName("Should expose an unmodifiable allowedHosts view")
        void shouldExposeUnmodifiableAllowedHosts() {
            Set<String> hosts = RedirectPolicy.builder().allowedHosts(List.of("cdn.example.net")).build().getAllowedHosts();

            assertThrows(UnsupportedOperationException.class, () -> hosts.add("evil.example.org"));
        }

        @Test
        @DisplayName("Should reject a blank allowedHosts entry")
        void shouldRejectBlankAllowedHost() {
            RedirectPolicy.RedirectPolicyBuilder builder = RedirectPolicy.builder();
            List<String> hosts = List.of("  ");

            assertThrows(IllegalArgumentException.class, () -> builder.allowedHosts(hosts));
        }
    }

    @Nested
    @DisplayName("forwardsCredentials")
    class ForwardsCredentialsTests {

        private static final URI ALLOWLISTED = URI.create("https://cdn.example.net/asset");
        private static final URI SAME_ORIGIN = URI.create("https://api.example.com/v2/other");

        private static RedirectPolicy policyWith(CredentialForwarding strategy) {
            return RedirectPolicy.builder()
                    .allowedHosts(List.of("cdn.example.net"))
                    .credentialForwarding(strategy)
                    .build();
        }

        @Test
        @DisplayName("Should forward credentials on a same-origin hop under STRIP_ON_CROSS_ORIGIN")
        void shouldForwardSameOriginUnderStrip() {
            assertTrue(policyWith(CredentialForwarding.STRIP_ON_CROSS_ORIGIN).forwardsCredentials(ORIGIN, SAME_ORIGIN));
        }

        @Test
        @DisplayName("Should strip credentials on an allowlisted cross-host hop under STRIP_ON_CROSS_ORIGIN")
        void shouldStripCrossOriginUnderStrip() {
            assertFalse(policyWith(CredentialForwarding.STRIP_ON_CROSS_ORIGIN).forwardsCredentials(ORIGIN, ALLOWLISTED));
        }

        @Test
        @DisplayName("Should forward credentials on a same-origin hop under FORWARD_TO_ALLOWLISTED")
        void shouldForwardSameOriginUnderForward() {
            assertTrue(policyWith(CredentialForwarding.FORWARD_TO_ALLOWLISTED).forwardsCredentials(ORIGIN, SAME_ORIGIN));
        }

        @Test
        @DisplayName("Should forward credentials on an allowlisted cross-host hop under FORWARD_TO_ALLOWLISTED")
        void shouldForwardCrossOriginUnderForward() {
            assertTrue(policyWith(CredentialForwarding.FORWARD_TO_ALLOWLISTED).forwardsCredentials(ORIGIN, ALLOWLISTED));
        }

        @Test
        @DisplayName("Should strip credentials on a same-origin hop for an unnamed policy — the default is STRIP_ON_CROSS_ORIGIN")
        void shouldDefaultToStripOnCrossOrigin() {
            RedirectPolicy unnamed = RedirectPolicy.builder().allowedHosts(List.of("cdn.example.net")).build();

            assertEquals(CredentialForwarding.STRIP_ON_CROSS_ORIGIN, unnamed.getCredentialForwarding());
            assertFalse(unnamed.forwardsCredentials(ORIGIN, ALLOWLISTED));
            assertTrue(unnamed.forwardsCredentials(ORIGIN, SAME_ORIGIN));
        }

        @Test
        @DisplayName("Should not let FORWARD_TO_ALLOWLISTED change any refuse verdict")
        void shouldNotChangeRefuseVerdicts() {
            RedirectPolicy forwarding = policyWith(CredentialForwarding.FORWARD_TO_ALLOWLISTED);

            assertRefused(forwarding, ORIGIN, URI.create("https://evil.example.org/asset"), RedirectRefusal.CROSS_ORIGIN);
            assertRefused(forwarding, ORIGIN, URI.create("http://cdn.example.net/asset"), RedirectRefusal.PROTOCOL_DOWNGRADE);
            assertPermitted(forwarding, ORIGIN, ALLOWLISTED);
        }
    }

    @Nested
    @DisplayName("RedirectNotAllowedException")
    class RedirectNotAllowedExceptionTests {

        @Test
        @DisplayName("Should carry from, to and reason and name all three in the message")
        void shouldCarryHopAndReason() {
            URI to = URI.create("https://evil.example.org/asset");

            RedirectNotAllowedException exception = new RedirectNotAllowedException(ORIGIN, to, RedirectRefusal.CROSS_ORIGIN);

            assertEquals(ORIGIN, exception.getFrom());
            assertEquals(to, exception.getTo());
            assertEquals(RedirectRefusal.CROSS_ORIGIN, exception.getReason());
            String message = exception.getMessage();
            assertNotNull(message);
            assertTrue(message.contains(ORIGIN.toString()), message);
            assertTrue(message.contains(to.toString()), message);
            assertTrue(message.contains(RedirectRefusal.CROSS_ORIGIN.name()), message);
        }

        @Test
        @DisplayName("Should allow a null target for TOO_MANY_HOPS")
        void shouldAllowNullTargetForHopBudget() {
            RedirectNotAllowedException exception = new RedirectNotAllowedException(ORIGIN, null, RedirectRefusal.TOO_MANY_HOPS);

            assertNull(exception.getTo());
            assertEquals(RedirectRefusal.TOO_MANY_HOPS, exception.getReason());
        }

        @Test
        @DisplayName("Should classify as the non-retryable CONFIGURATION_ERROR without any HttpErrorCategory change")
        void shouldClassifyAsConfigurationError() {
            RedirectNotAllowedException exception = new RedirectNotAllowedException(ORIGIN, null, RedirectRefusal.TOO_MANY_HOPS);

            assertEquals(HttpErrorCategory.CONFIGURATION_ERROR, HttpErrorCategory.fromException(exception));
            assertFalse(HttpErrorCategory.CONFIGURATION_ERROR.isRetryable());
        }
    }
}
