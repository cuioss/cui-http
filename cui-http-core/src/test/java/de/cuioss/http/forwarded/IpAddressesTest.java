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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IP utilities")
class IpAddressesTest {

    @Nested
    @DisplayName("IpAddresses.parseChainEntry")
    class ParseChainEntry {

        /**
         * The four shape families a chain entry arrives in, each pinned to the exact address it must
         * resolve to rather than merely to having resolved to something. An {@code assertNotNull}
         * sweep passes just as happily when a port suffix leaks into the address or a bracketed
         * literal comes back as a different host, so it cannot distinguish parsing the entry from
         * misreading it.
         *
         * <p>Stating the canonical form here also carries the port-stripping contract as a value:
         * {@code 192.0.2.7:443} resolving to {@code 192.0.2.7} is that rule, asserted where the
         * entry is parsed instead of in a separate case.</p>
         */
        @ParameterizedTest(name = "\"{0}\" resolves to {1}")
        @CsvSource({
                "192.0.2.7,192.0.2.7",
                "192.0.2.7:443,192.0.2.7",
                "[2001:db8::1]:443,2001:db8:0:0:0:0:0:1",
                "2001:db8::1,2001:db8:0:0:0:0:0:1"})
        @DisplayName("parses IPv4, IPv4:port, bracketed IPv6 and bare IPv6 to their canonical form")
        void parsesUsableForms(String entry, String expectedCanonical) {
            InetAddress parsed = IpAddresses.parseChainEntry(entry);

            assertNotNull(parsed, () -> entry + " must parse to an address");
            assertEquals(expectedCanonical, IpAddresses.canonical(parsed),
                    () -> entry + " must name exactly one address");
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

        /**
         * The regression population for the tightened guards, pinned to the exact address each entry
         * names. A guard that starts reading {@code 10.0.0.5} as a different host has not been kept
         * from rejecting the entry — it has been kept from rejecting it while silently changing which
         * hop it identifies, and only the canonical form makes that visible.
         */
        @ParameterizedTest(name = "\"{0}\" still resolves to {1}")
        @CsvSource({
                "[2001:db8::1],2001:db8:0:0:0:0:0:1",
                "[2001:db8::1]:443,2001:db8:0:0:0:0:0:1",
                "0.0.0.0,0.0.0.0",
                "10.0.0.5,10.0.0.5",
                "2001:db8::1,2001:db8:0:0:0:0:0:1",
                "255.255.255.255,255.255.255.255",
                "192.168.1.1,192.168.1.1"})
        @DisplayName("still parses the valid literals the tightened guards must not affect")
        void acceptsValidLiterals(String entry, String expectedCanonical) {
            InetAddress parsed = IpAddresses.parseChainEntry(entry);

            assertNotNull(parsed, () -> entry + " must still parse");
            assertEquals(expectedCanonical, IpAddresses.canonical(parsed),
                    () -> entry + " must still name the same address it always did");
        }

        /**
         * The bracketed branch has always refused a port suffix that is not a digit run; the
         * unbracketed one used to read the address out of such a token and discard the remainder,
         * which honors a hop nobody wrote. {@link #parsesUsableForms(String, String)} carries the
         * matched positive control that {@code 192.0.2.7:443} still parses, and to which address.
         */
        @ParameterizedTest(name = "\"{0}\" carries a port suffix that is not a digit run")
        @ValueSource(strings = {"192.0.2.7:", "192.0.2.7:notaport", "192.0.2.7:44 3"})
        @DisplayName("rejects an unbracketed entry whose port suffix is not a digit run")
        void rejectsMalformedUnbracketedPortSuffix(String entry) {
            assertNull(IpAddresses.parseChainEntry(entry),
                    "an entry is malformed as a whole, whichever spelling it arrived in");
        }

        @Test
        @DisplayName("holds an IPv4-mapped IPv6 literal to the IPv4 octet rules")
        void appliesIpv4RulesToMappedSuffix() {
            assertAll("the mapped spelling must not be the way around the dotted-quad guards",
                    () -> assertNull(IpAddresses.parse("::ffff:010.0.0.5"),
                            "a leading-zero octet is an allow-list bypass in the mapped form too"),
                    () -> assertNull(IpAddresses.parse("::ffff:999.1.1.1"),
                            "an out-of-range octet must not reach getByName from the mapped form either"),
                    () -> assertNotNull(IpAddresses.parse("::ffff:10.0.0.5"),
                            "positive control: a well-formed mapped literal still resolves, so the "
                                    + "guard rejects the octets rather than the mapped form"));
        }

        @Test
        @DisplayName("rejects an IPv6 literal carrying a zone/scope ID")
        void rejectsZoneId() {
            assertAll("a scope ID is meaningful only on its own interface, so it identifies no hop",
                    () -> assertNull(IpAddresses.parse("fe80::1%eth0"),
                            "'%' is outside the IPv6 character class, so the value never reaches getByName"),
                    () -> assertNull(IpAddresses.parseChainEntry("fe80::1%eth0"),
                            "the chain entry inherits parse's literal rules"),
                    () -> assertNotNull(IpAddresses.parse("fe80::1"),
                            "positive control: the same literal without a zone ID still resolves"));
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

        /**
         * The widest prefix an operator can write, and the one whose blast radius is easiest to
         * misjudge: {@code /0} trusts every address of its family, which is a real configuration and
         * must behave as written rather than being quietly narrowed. The family half is what keeps it
         * from being "trusts everything" — an IPv6 peer is still outside an IPv4 range however wide.
         */
        @Test
        @DisplayName("a /0 prefix matches every address of its own family and none of the other")
        void zeroPrefixMatchesWholeFamily() {
            CidrRange range = CidrRange.parse("10.0.0.0/0");

            assertAll("a zero-bit prefix compares no bits, but still compares the family",
                    () -> assertTrue(range.contains(IpAddresses.parse("203.0.113.7")),
                            "no bits are compared, so an arbitrary IPv4 address falls inside"),
                    () -> assertFalse(range.contains(IpAddresses.parse("2001:db8::1")),
                            "an IPv4 range never admits an IPv6 candidate, however wide the prefix"));
        }

        /**
         * A prefix outside the family's width is an operator typo that would otherwise be resolved
         * into <em>some</em> range and silently trusted. It must fail loudly at parse time, and the
         * message must name the range it rejected — an operator reading a bare "out of range" in a
         * startup log cannot tell which of several configured entries was the bad one.
         */
        @ParameterizedTest(name = "\"{0}\" is not a usable prefix width")
        @ValueSource(strings = {"10.0.0.0/-1", "10.0.0.0/33"})
        @DisplayName("rejects a prefix outside the family's width, naming the offending range")
        void rejectsOutOfRangePrefix(String spec) {
            var thrown = assertThrows(IllegalArgumentException.class, () -> CidrRange.parse(spec));

            assertTrue(thrown.getMessage().contains(spec),
                    () -> "the message must name the rejected range, but was: " + thrown.getMessage());
        }

        /**
         * The IPv6 counterpart of {@link #bareLiteralMatchesItself()}: a full-width prefix is a
         * single host written in CIDR form, so it must admit the literal itself and nothing adjacent
         * to it. Pinned because the masking loop's per-byte arithmetic has no remainder bits at 128
         * and so takes a different path than the sub-byte case above.
         */
        @Test
        @DisplayName("a /128 prefix matches only the exact IPv6 literal")
        void fullWidthIpv6PrefixMatchesOnlyItself() {
            CidrRange range = CidrRange.parse("2001:db8::/128");

            assertAll("a full-width prefix compares every bit",
                    () -> assertTrue(range.contains(IpAddresses.parse("2001:db8::")),
                            "the literal itself is inside its own single-host range"),
                    () -> assertFalse(range.contains(IpAddresses.parse("2001:db8::1")),
                            "the neighbouring address differs in the last bit, so it is outside"));
        }
    }
}
