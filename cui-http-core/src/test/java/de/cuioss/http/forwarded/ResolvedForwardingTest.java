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
        @DisplayName("rejects a scheme outside the http/https allow-list")
        void rejectsForeignScheme() {
            assertAll("scheme allow-list",
                    () -> assertThrows(IllegalArgumentException.class, () -> withScheme("ftp")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withScheme("HTTPS"),
                            "the resolver lower-cases the scheme, so a mixed-case value is not canonical"),
                    () -> assertThrows(IllegalArgumentException.class, () -> withScheme("")));
        }

        @Test
        @DisplayName("rejects a port outside 1..65535")
        void rejectsPortOutsideRange() {
            assertAll("port range",
                    () -> assertThrows(IllegalArgumentException.class, () -> withPort(0)),
                    () -> assertThrows(IllegalArgumentException.class, () -> withPort(65536)),
                    () -> assertThrows(IllegalArgumentException.class, () -> withPort(-1)));
        }

        @Test
        @DisplayName("rejects a context path that is not in normalized shape")
        void rejectsDenormalizedContextPath() {
            assertAll("context-path shape",
                    () -> assertThrows(IllegalArgumentException.class, () -> withContextPath("ui"),
                            "a context path needs a leading slash"),
                    () -> assertThrows(IllegalArgumentException.class, () -> withContextPath("//ui"),
                            "a protocol-relative prefix must not survive construction"),
                    () -> assertThrows(IllegalArgumentException.class, () -> withContextPath("/ui/"),
                            "a trailing slash is not the normalized shape"),
                    () -> assertThrows(IllegalArgumentException.class, () -> withContextPath("/")));
        }

        @Test
        @DisplayName("rejects a control character in any component, so CR/LF cannot round-trip out")
        void rejectsControlCharacters() {
            assertAll("control characters",
                    () -> assertThrows(IllegalArgumentException.class, () -> withHost("app.example.com\r\nX: y")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withContextPath("/ui\r\nX: y")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withClientIp("203.0.113.7\r\nX: y")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withHost("app.example.com\t")));
        }

        @Test
        @DisplayName("rejects a present-but-blank host or client IP")
        void rejectsBlankPresentComponents() {
            assertAll("blank components",
                    () -> assertThrows(IllegalArgumentException.class, () -> withHost("")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withHost("   ")),
                    () -> assertThrows(IllegalArgumentException.class, () -> withClientIp("")));
        }

        @Test
        @DisplayName("names the violated component without echoing the offending value")
        void namesComponentWithoutEchoingValue() {
            var thrown = assertThrows(IllegalArgumentException.class, () -> withHost("evil\r\nX: y"));

            assertAll("message content",
                    () -> assertTrue(thrown.getMessage().contains("host"), "the message names the component"),
                    () -> assertFalse(thrown.getMessage().contains("evil"),
                            "the message must not echo untrusted input"));
        }

        @Test
        @DisplayName("accepts every valid shape, including the port range boundaries")
        void acceptsValidShapes() {
            assertAll("valid shapes",
                    () -> assertDoesNotThrow(() -> withScheme("http")),
                    () -> assertDoesNotThrow(() -> withScheme("https")),
                    () -> assertDoesNotThrow(() -> withPort(1)),
                    () -> assertDoesNotThrow(() -> withPort(65535)),
                    () -> assertDoesNotThrow(() -> withContextPath("")),
                    () -> assertDoesNotThrow(() -> withContextPath("/ui")),
                    () -> assertDoesNotThrow(() -> withContextPath("/ui/admin")),
                    () -> assertDoesNotThrow(() -> withHost("app.example.com")),
                    () -> assertDoesNotThrow(() -> withHost("[2001:db8::1]")),
                    () -> assertDoesNotThrow(() -> withClientIp("2001:db8::1")));
        }

        private static ResolvedForwarding withScheme(String scheme) {
            return new ResolvedForwarding(Optional.of(scheme), Optional.empty(), OptionalInt.empty(),
                    "", Optional.empty());
        }

        private static ResolvedForwarding withHost(String host) {
            return new ResolvedForwarding(Optional.empty(), Optional.of(host), OptionalInt.empty(),
                    "", Optional.empty());
        }

        private static ResolvedForwarding withPort(int port) {
            return new ResolvedForwarding(Optional.empty(), Optional.empty(), OptionalInt.of(port),
                    "", Optional.empty());
        }

        private static ResolvedForwarding withContextPath(String contextPath) {
            return new ResolvedForwarding(Optional.empty(), Optional.empty(), OptionalInt.empty(),
                    contextPath, Optional.empty());
        }

        private static ResolvedForwarding withClientIp(String clientIp) {
            return new ResolvedForwarding(Optional.empty(), Optional.empty(), OptionalInt.empty(),
                    "", Optional.of(clientIp));
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

        @Test
        @DisplayName("omits a host-less port (RFC 7239 has no standalone port directive)")
        void omitsHostLessPort() {
            var forwarding = new ResolvedForwarding(Optional.empty(), Optional.empty(),
                    OptionalInt.of(8443), "", Optional.empty());

            assertAll("a port is expressible only folded into host=\"name:port\"",
                    () -> assertTrue(forwarding.toForwardedHeader().isEmpty(),
                            "emitting host=\":8443\" would compose an invalid authority"),
                    () -> assertEquals("8443",
                            forwarding.toXForwardedHeaders().get("X-Forwarded-Port"),
                            "X-Forwarded-Port is emitted independently of the host, so it survives"));
        }

        @Test
        @DisplayName("drops a host-less port while still emitting the accompanying proto")
        void dropsHostLessPortAlongsideProto() {
            var forwarding = new ResolvedForwarding(Optional.of("https"), Optional.empty(),
                    OptionalInt.of(8443), "", Optional.empty());

            assertEquals(Optional.of("proto=https"), forwarding.toForwardedHeader(),
                    "the port has no host to fold into, so it is omitted rather than emitted alone");
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
