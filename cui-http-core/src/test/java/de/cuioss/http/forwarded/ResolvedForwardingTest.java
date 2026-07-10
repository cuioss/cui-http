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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResolvedForwarding")
class ResolvedForwardingTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("empty() carries no fields and an empty context path")
        void emptyHasNoFields() {
            ResolvedForwarding empty = ResolvedForwarding.empty();

            assertTrue(empty.scheme().isEmpty());
            assertTrue(empty.host().isEmpty());
            assertTrue(empty.port().isEmpty());
            assertEquals("", empty.contextPath());
            assertTrue(empty.clientIp().isEmpty());
        }

        @Test
        @DisplayName("null components are normalized to empty by the canonical constructor")
        void nullComponentsNormalized() {
            ResolvedForwarding value = new ResolvedForwarding(null, null, null, null, null);

            assertEquals(ResolvedForwarding.empty(), value,
                    "A fully-null construction must equal the empty result");
        }
    }

    @Nested
    @DisplayName("toXForwardedHeaders")
    class ToXForwardedHeaders {

        @Test
        @DisplayName("emits only the present fields")
        void emitsOnlyPresentFields() {
            var forwarding = new ResolvedForwarding(Optional.of("https"), Optional.of("app.example.com"),
                    OptionalInt.of(8443), "/ui", Optional.of("203.0.113.7"));

            Map<String, String> headers = forwarding.toXForwardedHeaders();

            assertEquals("https", headers.get("X-Forwarded-Proto"));
            assertEquals("app.example.com", headers.get("X-Forwarded-Host"));
            assertEquals("8443", headers.get("X-Forwarded-Port"));
            assertEquals("/ui", headers.get("X-Forwarded-Prefix"));
            assertEquals("203.0.113.7", headers.get("X-Forwarded-For"));
            assertEquals(5, headers.size());
        }

        @Test
        @DisplayName("omits absent fields, including an empty context path")
        void omitsAbsentFields() {
            var forwarding = new ResolvedForwarding(Optional.of("http"), Optional.empty(),
                    OptionalInt.empty(), "", Optional.empty());

            Map<String, String> headers = forwarding.toXForwardedHeaders();

            assertEquals(Map.of("X-Forwarded-Proto", "http"), headers);
        }

        @Test
        @DisplayName("empty() serializes to no headers")
        void emptySerializesToNoHeaders() {
            assertTrue(ResolvedForwarding.empty().toXForwardedHeaders().isEmpty());
        }
    }

    @Nested
    @DisplayName("toForwardedHeader")
    class ToForwardedHeader {

        @Test
        @DisplayName("emits for/host/proto directives; folds port into host")
        void emitsDirectives() {
            var forwarding = new ResolvedForwarding(Optional.of("https"), Optional.of("app.example.com"),
                    OptionalInt.of(8443), "/ui", Optional.of("203.0.113.7"));

            assertEquals(Optional.of("for=203.0.113.7;host=\"app.example.com:8443\";proto=https"),
                    forwarding.toForwardedHeader());
        }

        @Test
        @DisplayName("brackets and quotes an IPv6 client address")
        void bracketsIpv6ClientAddress() {
            var forwarding = new ResolvedForwarding(Optional.empty(), Optional.empty(),
                    OptionalInt.empty(), "", Optional.of("2001:db8::1"));

            assertEquals(Optional.of("for=\"[2001:db8::1]\""), forwarding.toForwardedHeader());
        }

        @Test
        @DisplayName("omits the context path (RFC 7239 has no prefix directive)")
        void omitsContextPath() {
            var forwarding = new ResolvedForwarding(Optional.empty(), Optional.empty(),
                    OptionalInt.empty(), "/ui", Optional.empty());

            assertTrue(forwarding.toForwardedHeader().isEmpty(),
                    "A result carrying only a context path has no Forwarded-expressible field");
        }

        @Test
        @DisplayName("emits an unquoted host when it is a valid token")
        void emitsUnquotedTokenHost() {
            var forwarding = new ResolvedForwarding(Optional.of("http"), Optional.of("example.com"),
                    OptionalInt.empty(), "", Optional.empty());

            assertEquals(Optional.of("host=example.com;proto=http"), forwarding.toForwardedHeader());
        }

        @Test
        @DisplayName("empty() serializes to no Forwarded value")
        void emptySerializesToNothing() {
            assertTrue(ResolvedForwarding.empty().toForwardedHeader().isEmpty());
        }
    }

    @Nested
    @DisplayName("Interplay")
    class Interplay {

        @Test
        @DisplayName("X-Forwarded-Prefix carries the context path that Forwarded cannot")
        void prefixCarriesContextPath() {
            var forwarding = new ResolvedForwarding(Optional.empty(), Optional.empty(),
                    OptionalInt.empty(), "/gateway", Optional.empty());

            assertTrue(forwarding.toForwardedHeader().isEmpty());
            assertEquals("/gateway", forwarding.toXForwardedHeaders().get("X-Forwarded-Prefix"));
            assertFalse(forwarding.toXForwardedHeaders().containsKey("X-Forwarded-Proto"));
        }
    }
}
