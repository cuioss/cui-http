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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RfcForwardedParser")
class RfcForwardedParserTest {

    @Test
    @DisplayName("extracts proto, host, and the ordered for chain")
    void extractsDirectives() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse(
                "for=203.0.113.7;host=app.example.com;proto=https, for=10.0.0.5");

        assertEquals("https", parsed.proto().orElseThrow());
        assertEquals("app.example.com", parsed.host().orElseThrow());
        assertEquals(List.of("203.0.113.7", "10.0.0.5"), parsed.forValues());
    }

    @Test
    @DisplayName("unquotes quoted values and keeps commas inside quotes")
    void handlesQuotedValues() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("for=\"[2001:db8::1]:443\";host=\"a,b\"");

        assertEquals("[2001:db8::1]:443", parsed.forValues().getFirst());
        assertEquals("a,b", parsed.host().orElseThrow());
    }

    @Test
    @DisplayName("unescapes backslash-escaped characters inside a quoted value")
    void unescapesEscapedCharacters() {
        // host="a\"b" -> a"b ; for="x\\y" -> x\y
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("host=\"a\\\"b\";for=\"x\\\\y\"");

        assertEquals("a\"b", parsed.host().orElseThrow());
        assertEquals("x\\y", parsed.forValues().getFirst());
    }

    @Test
    @DisplayName("takes the last proto across elements")
    void lastProtoWins() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("proto=https, proto=http");

        assertEquals("http", parsed.proto().orElseThrow(), "the nearest hop appends last, so its proto wins");
    }

    @Test
    @DisplayName("takes the last host across elements")
    void lastHostWins() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("host=attacker.example, host=app.example.com");

        assertEquals("app.example.com", parsed.host().orElseThrow(),
                "the nearest hop appends last, so its host wins");
    }

    @Test
    @DisplayName("is case-insensitive on directive names and ignores unknown directives")
    void caseInsensitiveAndIgnoresUnknown() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("Proto=https;By=10.0.0.1;ext=x");

        assertEquals("https", parsed.proto().orElseThrow());
        assertTrue(parsed.host().isEmpty());
        assertTrue(parsed.forValues().isEmpty());
    }

    @Nested
    @DisplayName("malformed forwarded-pair")
    class MalformedPairs {

        @Test
        @DisplayName("rejects the whole header when a pair carries an empty value")
        void rejectsEmptyValue() {
            RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("proto=;host");

            assertAll("rejected header",
                    () -> assertTrue(parsed.malformed(), "proto= carries no value, so the header is malformed"),
                    () -> assertTrue(parsed.proto().isEmpty(), "a malformed header reports no proto"),
                    () -> assertTrue(parsed.host().isEmpty(), "a malformed header reports no host"),
                    () -> assertTrue(parsed.forValues().isEmpty(), "a malformed header reports no for chain"));
        }

        @Test
        @DisplayName("rejects the whole header when a non-blank pair carries no '='")
        void rejectsPairWithoutEquals() {
            RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("host");

            assertTrue(parsed.malformed(), "a pair without '=' violates the forwarded-pair grammar");
        }

        @Test
        @DisplayName("rejects the whole header when a pair carries an empty directive name")
        void rejectsEmptyDirectiveName() {
            RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("=https");

            assertTrue(parsed.malformed(), "an empty directive name violates the forwarded-pair grammar");
        }

        @Test
        @DisplayName("accepts a grammatically legal blank pair and still parses the header")
        void acceptsLegalBlankPair() {
            RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("for=203.0.113.7;");

            assertAll("accepted header",
                    () -> assertFalse(parsed.malformed(),
                            "RFC 7239 §4 makes forwarded-pair optional, so a trailing ';' is legal"),
                    () -> assertEquals(List.of("203.0.113.7"), parsed.forValues()));
        }

        @Test
        @DisplayName("stops parsing at the first malformed pair")
        void stopsAtFirstMalformedPair() {
            RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("host, proto=https");

            assertAll("abort before the later element",
                    () -> assertTrue(parsed.malformed()),
                    () -> assertTrue(parsed.proto().isEmpty(),
                            "the trailing proto is not accumulated once the header is rejected"));
        }
    }
}
