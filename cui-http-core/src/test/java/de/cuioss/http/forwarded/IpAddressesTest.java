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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IP utilities")
class IpAddressesTest {

    @Nested
    @DisplayName("IpAddresses.parseChainEntry")
    class ParseChainEntry {

        @Test
        @DisplayName("parses IPv4, IPv4:port, bracketed IPv6, and bare IPv6")
        void parsesUsableForms() {
            assertNotNull(IpAddresses.parseChainEntry("192.0.2.7"));
            assertNotNull(IpAddresses.parseChainEntry("192.0.2.7:443"));
            assertNotNull(IpAddresses.parseChainEntry("[2001:db8::1]:443"));
            assertNotNull(IpAddresses.parseChainEntry("2001:db8::1"));
        }

        @ParameterizedTest(name = "\"{0}\" is not a usable node identifier")
        @ValueSource(strings = {"unknown", "UNKNOWN", "_hidden", "not-an-ip", "[2001:db8::1", "", "   "})
        @DisplayName("rejects unknown/obfuscated/malformed entries")
        void rejectsUnusable(String entry) {
            assertNull(IpAddresses.parseChainEntry(entry));
        }

        @ParameterizedTest(name = "\"{0}\" carries content after the closing bracket")
        @ValueSource(strings = {"[::1]garbage", "[::1]:notaport", "[::1]:", "[::1]x:8443", "[::1]]"})
        @DisplayName("rejects trailing content after a closing bracket")
        void rejectsBracketTrailingContent(String entry) {
            assertNull(IpAddresses.parseChainEntry(entry),
                    "only a colon plus ASCII digits may follow the closing bracket");
        }

        @ParameterizedTest(name = "\"{0}\" carries a leading-zero octet")
        @ValueSource(strings = {"010.0.0.5", "192.168.01.1", "0.0.0.05", "00.0.0.1"})
        @DisplayName("rejects an IPv4 literal with a leading-zero octet")
        void rejectsLeadingZeroOctets(String entry) {
            assertNull(IpAddresses.parseChainEntry(entry),
                    "a leading-zero octet is read as octal by some resolvers, so it is an allow-list bypass");
        }

        @ParameterizedTest(name = "\"{0}\" carries an out-of-range octet")
        @ValueSource(strings = {"999.1.1.1", "256.1.1.1", "1.1.1.256", "300.400.500.600"})
        @DisplayName("rejects an IPv4 literal with an out-of-range octet without a DNS lookup")
        void rejectsOutOfRangeOctets(String entry) {
            assertNull(IpAddresses.parseChainEntry(entry),
                    "InetAddress.getByName treats an unparseable dotted-quad as a hostname and resolves it, "
                            + "so an out-of-range octet must be rejected by the pattern to keep parsing literal-only");
        }

        @ParameterizedTest(name = "\"{0}\" is still a usable node identifier")
        @ValueSource(strings = {"[2001:db8::1]", "[2001:db8::1]:443", "0.0.0.0", "10.0.0.5", "2001:db8::1",
                "255.255.255.255", "192.168.1.1"})
        @DisplayName("still parses the valid literals the tightened guards must not affect")
        void acceptsValidLiterals(String entry) {
            assertNotNull(IpAddresses.parseChainEntry(entry));
        }

        @Test
        @DisplayName("canonicalizes to the host address form")
        void canonicalizes() {
            InetAddress address = IpAddresses.parseChainEntry("192.0.2.7:443");
            assertNotNull(address);
            assertEquals("192.0.2.7", IpAddresses.canonical(address));
        }
    }

    @Nested
    @DisplayName("CidrRange")
    class Cidr {

        @Test
        @DisplayName("a bare literal matches only itself")
        void bareLiteralMatchesItself() {
            CidrRange range = CidrRange.parse("192.168.1.1");
            assertTrue(range.contains(IpAddresses.parse("192.168.1.1")));
            assertFalse(range.contains(IpAddresses.parse("192.168.1.2")));
        }

        @Test
        @DisplayName("an IPv4 range never matches an IPv6 candidate")
        void familyMismatch() {
            CidrRange range = CidrRange.parse("10.0.0.0/8");
            assertFalse(range.contains(IpAddresses.parse("2001:db8::1")));
        }

        @Test
        @DisplayName("matches within a sub-byte prefix boundary")
        void subBytePrefix() {
            CidrRange range = CidrRange.parse("203.0.113.0/28");
            assertTrue(range.contains(IpAddresses.parse("203.0.113.5")));
            assertFalse(range.contains(IpAddresses.parse("203.0.113.20")));
        }
    }
}
