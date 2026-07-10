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

import de.cuioss.http.security.config.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ForwardedResolverConfig")
@SuppressWarnings("java:S5778") // assertThrows lambdas intentionally wrap the whole failing call chain
class ForwardedResolverConfigTest {

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("secureDefault honors nothing")
        void secureDefaultHonorsNothing() {
            ForwardedResolverConfig config = ForwardedResolverConfig.secureDefault();

            assertFalse(config.trustAll());
            assertTrue(config.allowedContextPaths().isEmpty());
            assertTrue(config.trustedProxies().isEmpty());
            assertEquals(SecurityConfiguration.defaults(), config.securityConfig());
        }
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("normalizes allowed context paths and drops empties")
        void normalizesAllowedContextPaths() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .allowedContextPaths(new LinkedHashSet<>(List.of("app/", "/gw", "/", "  ")))
                    .build();

            assertEquals(Set.of("/app", "/gw"), config.allowedContextPaths());
        }

        @Test
        @DisplayName("returns unmodifiable views")
        void returnsUnmodifiableViews() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .allowedContextPaths(Set.of("/app"))
                    .trustedProxies(Set.of("10.0.0.0/8"))
                    .build();

            assertThrows(UnsupportedOperationException.class, () -> config.allowedContextPaths().add("/x"));
            assertThrows(UnsupportedOperationException.class, () -> config.trustedProxies().add("x"));
        }

        @Test
        @DisplayName("rejects null setters")
        void rejectsNullSetters() {
            ForwardedResolverConfig.Builder builder = ForwardedResolverConfig.builder();
            assertThrows(NullPointerException.class, () -> builder.allowedContextPaths(null));
            assertThrows(NullPointerException.class, () -> builder.trustedProxies(null));
            assertThrows(NullPointerException.class, () -> builder.securityConfig(null));
        }

        @ParameterizedTest(name = "rejects malformed trusted proxy \"{0}\"")
        @ValueSource(strings = {"not-an-ip", "10.0.0.0/99", "example.com", "10.0.0.0/", "999.1.1.1/8"})
        @DisplayName("rejects malformed trusted-proxy specs")
        void rejectsMalformedTrustedProxies(String spec) {
            assertThrows(IllegalArgumentException.class,
                    () -> ForwardedResolverConfig.builder().trustedProxies(Set.of(spec)));
        }

        @Test
        @DisplayName("accepts IPv4, IPv6, CIDR, and bare-literal trusted proxies")
        void acceptsValidTrustedProxies() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .trustedProxies(new LinkedHashSet<>(List.of("10.0.0.0/8", "192.168.1.1", "2001:db8::/32")))
                    .build();

            assertEquals(3, config.trustedProxies().size());
        }

        @Test
        @DisplayName("skips blank trusted-proxy entries")
        void skipsBlankTrustedProxies() {
            ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                    .trustedProxies(new LinkedHashSet<>(List.of("10.0.0.0/8", "   ")))
                    .build();

            assertEquals(Set.of("10.0.0.0/8"), config.trustedProxies());
        }
    }

    @Nested
    @DisplayName("parseAllowlist")
    class ParseAllowlist {

        @Test
        @DisplayName("normalizes and preserves input order, dropping empties")
        void normalizesAndOrders() {
            Set<String> allowed = ForwardedResolverConfig.parseAllowlist("nifi-proxy, /gw/, , /, //attacker.com");

            assertEquals(List.of("/nifi-proxy", "/gw"), new ArrayList<>(allowed));
        }

        @Test
        @DisplayName("returns an empty unmodifiable set for null/blank input")
        void emptyForNullOrBlank() {
            assertTrue(ForwardedResolverConfig.parseAllowlist(null).isEmpty());
            assertTrue(ForwardedResolverConfig.parseAllowlist("   ").isEmpty());
            assertThrows(UnsupportedOperationException.class,
                    () -> ForwardedResolverConfig.parseAllowlist("/app").add("/x"));
        }
    }
}
