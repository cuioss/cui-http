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
