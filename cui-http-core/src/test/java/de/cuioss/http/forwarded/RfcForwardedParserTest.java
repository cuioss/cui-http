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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("takes the first proto/host across elements")
    void firstProtoWins() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("proto=https, proto=http");

        assertEquals("https", parsed.proto().orElseThrow());
    }

    @Test
    @DisplayName("is case-insensitive on directive names and ignores unknown directives")
    void caseInsensitiveAndIgnoresUnknown() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("Proto=https;By=10.0.0.1;ext=x");

        assertEquals("https", parsed.proto().orElseThrow());
        assertTrue(parsed.host().isEmpty());
        assertTrue(parsed.forValues().isEmpty());
    }

    @Test
    @DisplayName("ignores malformed pairs without a value")
    void ignoresMalformedPairs() {
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse("proto=;host");

        assertTrue(parsed.proto().isEmpty());
        assertTrue(parsed.host().isEmpty());
    }
}
