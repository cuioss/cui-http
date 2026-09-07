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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.core.ValidationType;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class CharacterValidationConstantsTest {

    @Test
    void shouldInitializeRFC3986UnreservedCharacters() {
        IntPredicate unreserved = CharacterValidationConstants.RFC3986_UNRESERVED;

        // Test ALPHA characters
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(unreserved.test(c), "Uppercase letter " + c + " should be allowed");
        }
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue(unreserved.test(c), "Lowercase letter " + c + " should be allowed");
        }

        // Test DIGIT characters
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(unreserved.test(c), "Digit " + c + " should be allowed");
        }

        // Test specific unreserved characters
        assertTrue(unreserved.test('-'));
        assertTrue(unreserved.test('.'));
        assertTrue(unreserved.test('_'));
        assertTrue(unreserved.test('~'));

        // Test some characters that should NOT be allowed
        assertFalse(unreserved.test(' '));
        assertFalse(unreserved.test('/'));
        assertFalse(unreserved.test('?'));
        assertFalse(unreserved.test('#'));
    }

    @Test
    @SuppressWarnings("java:S5961") // Exhaustive RFC 3986 path character-set membership check
    void shouldInitializeRFC3986PathCharacters() {
        IntPredicate pathChars = CharacterValidationConstants.RFC3986_PATH_CHARS;

        // Should include all unreserved characters
        assertTrue(pathChars.test('A'));
        assertTrue(pathChars.test('0'));
        assertTrue(pathChars.test('-'));

        // Should include path-specific characters
        assertTrue(pathChars.test('/'));
        assertTrue(pathChars.test('@'));
        assertTrue(pathChars.test(':'));

        // Should include sub-delims for path
        assertTrue(pathChars.test('!'));
        assertTrue(pathChars.test('$'));
        assertTrue(pathChars.test('&'));
        assertTrue(pathChars.test('\''));
        assertTrue(pathChars.test('('));
        assertTrue(pathChars.test(')'));
        assertTrue(pathChars.test('*'));
        assertTrue(pathChars.test('+'));
        assertTrue(pathChars.test(','));
        assertTrue(pathChars.test(';'));
        assertTrue(pathChars.test('='));

        // Should NOT include some characters
        assertFalse(pathChars.test(' '));
        assertFalse(pathChars.test('?'));
        assertFalse(pathChars.test('#'));
    }

    @Test
    void shouldInitializeRFC3986QueryCharacters() {
        IntPredicate queryChars = CharacterValidationConstants.RFC3986_QUERY_CHARS;

        // Should include all unreserved characters
        assertTrue(queryChars.test('A'));
        assertTrue(queryChars.test('0'));
        assertTrue(queryChars.test('-'));

        // Should include query-specific characters
        assertTrue(queryChars.test('?'));
        assertTrue(queryChars.test('&'));
        assertTrue(queryChars.test('='));

        // RFC 3986 section 3.4: query = *( pchar / "/" / "?" ), and pchar admits ":" and "@".
        // Browsers send all three unencoded, so rejecting them made the set narrower than the RFC.
        assertTrue(queryChars.test('/'), "RFC 3986 3.4 lists '/' as a legal query character");
        assertTrue(queryChars.test(':'), "RFC 3986 pchar admits ':' so it is legal in a query");
        assertTrue(queryChars.test('@'), "RFC 3986 pchar admits '@' so it is legal in a query");

        // Should include some sub-delims for query
        assertTrue(queryChars.test('!'));
        assertTrue(queryChars.test('$'));
        assertTrue(queryChars.test('\''));

        // Should NOT include some characters. '#' terminates the query component outright,
        // so it stays rejected - see DecodingStage.isParameterNameDelimiter for the decoded
        // spelling of the same verdict.
        assertFalse(queryChars.test(' '));
        assertFalse(queryChars.test('#'));
    }

    @Test
    @SuppressWarnings("java:S5961") // Exhaustive RFC 6265 cookie-octet character-set membership check
    void shouldInitializeRFC6265CookieOctetCharacters() {
        IntPredicate cookieOctet = CharacterValidationConstants.RFC6265_COOKIE_OCTET;

        // Should include ALPHA and DIGIT
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(cookieOctet.test(c), "Uppercase letter " + c + " should be allowed");
        }
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue(cookieOctet.test(c), "Lowercase letter " + c + " should be allowed");
        }
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(cookieOctet.test(c), "Digit " + c + " should be allowed");
        }

        // The base64 alphabet plus its padding character - the canonical legitimate cookie value
        // that RFC3986_UNRESERVED used to reject.
        assertTrue(cookieOctet.test('+'), "'+' is a cookie-octet member (base64 alphabet)");
        assertTrue(cookieOctet.test('/'), "'/' is a cookie-octet member (base64 alphabet)");
        assertTrue(cookieOctet.test('='), "'=' is a cookie-octet member (base64 padding)");

        // Boundary members of each cookie-octet range: %x21, %x23-2B, %x2D-3A, %x3C-5B, %x5D-7E
        assertTrue(cookieOctet.test(0x21));
        assertTrue(cookieOctet.test(0x23));
        assertTrue(cookieOctet.test(0x2B));
        assertTrue(cookieOctet.test(0x2D));
        assertTrue(cookieOctet.test(0x3A));
        assertTrue(cookieOctet.test(0x3C));
        assertTrue(cookieOctet.test(0x5B));
        assertTrue(cookieOctet.test(0x5D));
        assertTrue(cookieOctet.test(0x7E));

        // The excluded separators that fall between the ranges
        assertFalse(cookieOctet.test(','), "Comma separates cookie pairs and is excluded");
        assertFalse(cookieOctet.test(';'), "Semicolon separates cookie attributes and is excluded");
        assertFalse(cookieOctet.test('\\'), "Backslash is excluded from cookie-octet");
        assertFalse(cookieOctet.test(' '), "Whitespace is excluded from cookie-octet");

        // Every CTL, plus DEL and anything above it
        for (int c = 0; c <= 31; c++) {
            assertFalse(cookieOctet.test(c), "Control character 0x" + Integer.toHexString(c) + " must be rejected");
        }
        assertFalse(cookieOctet.test(0x7F), "DEL must be rejected");
        assertFalse(cookieOctet.test(0x80), "Extended ASCII must be rejected");
    }

    @Test
    void shouldRejectDoubleQuoteOutrightInCookieOctet() {
        IntPredicate cookieOctet = CharacterValidationConstants.RFC6265_COOKIE_OCTET;

        // RFC 6265 4.1.1 omits DQUOTE (0x22) from cookie-octet. The predicate is a pure
        // per-character membership test: it carries no quote-pair state, so there is no
        // matched-surrounding-DQUOTE carve-out.
        assertFalse(cookieOctet.test('"'), "DQUOTE is not a cookie-octet member");

        String quotedValue = "\"abc\"";
        assertFalse(cookieOctet.test(quotedValue.charAt(0)),
                "A matched-pair quoted value is rejected on its very first character");
        assertFalse(cookieOctet.test(quotedValue.charAt(quotedValue.length() - 1)),
                "The closing quote of a matched pair is rejected too");
        for (int i = 1; i < quotedValue.length() - 1; i++) {
            assertTrue(cookieOctet.test(quotedValue.charAt(i)),
                    "Only the quotes are rejected; the payload characters remain members");
        }
    }

    @Test
    @SuppressWarnings("java:S5961") // Exhaustive RFC 7230 header character-set membership check
    void shouldInitializeRFC7230HeaderCharacters() {
        IntPredicate headerChars = CharacterValidationConstants.RFC7230_HEADER_CHARS;

        // Should include most visible ASCII
        assertTrue(headerChars.test('A'));
        assertTrue(headerChars.test('0'));
        assertTrue(headerChars.test('-'));
        assertTrue(headerChars.test('_'));
        assertTrue(headerChars.test('/'));
        assertTrue(headerChars.test(':'));
        assertTrue(headerChars.test('='));

        // Should include space and tab
        assertTrue(headerChars.test(' '));
        assertTrue(headerChars.test('\t'));

        // Should exclude control characters
        assertFalse(headerChars.test('\0'));
        assertFalse(headerChars.test('\n'));
        assertFalse(headerChars.test('\r'));
        assertFalse(headerChars.test(1));

        // Should exclude characters outside printable range
        assertFalse(headerChars.test(127)); // DEL
        assertFalse(headerChars.test(31));  // Below space
    }

    @Test
    @SuppressWarnings("java:S5961") // Exhaustive RFC 7230 tchar (token) character-set membership check
    void shouldInitializeRFC7230TokenCharacters() {
        IntPredicate tokenChars = CharacterValidationConstants.RFC7230_TOKEN_CHARS;

        // Should include ALPHA
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(tokenChars.test(c), "Uppercase letter " + c + " should be allowed");
        }
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue(tokenChars.test(c), "Lowercase letter " + c + " should be allowed");
        }

        // Should include DIGIT
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(tokenChars.test(c), "Digit " + c + " should be allowed");
        }

        // Should include every tchar punctuation member (RFC 7230 section 3.2.6)
        for (char c : "!#$%&'*+-.^_`|~".toCharArray()) {
            assertTrue(tokenChars.test(c), "tchar punctuation " + c + " should be allowed");
        }

        // Should reject delimiters - these are the characters a header NAME must never contain
        assertFalse(tokenChars.test(' '), "Space must be rejected in a header name");
        assertFalse(tokenChars.test(':'), "Colon must be rejected in a header name");
        assertFalse(tokenChars.test(','));
        assertFalse(tokenChars.test(';'));
        assertFalse(tokenChars.test('('));
        assertFalse(tokenChars.test(')'));
        assertFalse(tokenChars.test('@'));
        assertFalse(tokenChars.test('/'));
        assertFalse(tokenChars.test('"'));

        // Should reject control characters and DEL
        assertFalse(tokenChars.test('\t'));
        assertFalse(tokenChars.test('\r'));
        assertFalse(tokenChars.test('\n'));
        assertFalse(tokenChars.test(127)); // DEL
    }

    @Test
    void shouldReturnCorrectCharacterSetForValidationType() {
        // The accessor now returns the shared immutable predicate instance (no defensive
        // copy), so identity equality is both correct and the strongest available assertion.
        assertSame(CharacterValidationConstants.RFC3986_PATH_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.URL_PATH));

        assertSame(CharacterValidationConstants.RFC3986_QUERY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.PARAMETER_NAME));
        assertSame(CharacterValidationConstants.RFC3986_QUERY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.PARAMETER_VALUE));

        assertSame(CharacterValidationConstants.RFC7230_TOKEN_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.HEADER_NAME));
        assertSame(CharacterValidationConstants.RFC7230_HEADER_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.HEADER_VALUE));

        assertSame(CharacterValidationConstants.HTTP_BODY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.BODY));
        assertSame(CharacterValidationConstants.RFC6265_COOKIE_OCTET,
                CharacterValidationConstants.getCharacterSet(ValidationType.COOKIE_NAME));
        assertSame(CharacterValidationConstants.RFC6265_COOKIE_OCTET,
                CharacterValidationConstants.getCharacterSet(ValidationType.COOKIE_VALUE));
    }

    @Test
    void shouldNotAllowNullValidationType() {
        assertThrows(NullPointerException.class, () ->
                CharacterValidationConstants.getCharacterSet(null));
    }
}
