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

        // Should include some sub-delims for query
        assertTrue(queryChars.test('!'));
        assertTrue(queryChars.test('$'));
        assertTrue(queryChars.test('\''));

        // Should NOT include some characters
        assertFalse(queryChars.test(' '));
        assertFalse(queryChars.test('#'));
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
    void shouldReturnCorrectCharacterSetForValidationType() {
        // The accessor now returns the shared immutable predicate instance (no defensive
        // copy), so identity equality is both correct and the strongest available assertion.
        assertSame(CharacterValidationConstants.RFC3986_PATH_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.URL_PATH));

        assertSame(CharacterValidationConstants.RFC3986_QUERY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.PARAMETER_NAME));
        assertSame(CharacterValidationConstants.RFC3986_QUERY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.PARAMETER_VALUE));

        assertSame(CharacterValidationConstants.RFC7230_HEADER_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.HEADER_NAME));
        assertSame(CharacterValidationConstants.RFC7230_HEADER_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.HEADER_VALUE));

        assertSame(CharacterValidationConstants.HTTP_BODY_CHARS,
                CharacterValidationConstants.getCharacterSet(ValidationType.BODY));
        assertSame(CharacterValidationConstants.RFC3986_UNRESERVED,
                CharacterValidationConstants.getCharacterSet(ValidationType.COOKIE_NAME));
        assertSame(CharacterValidationConstants.RFC3986_UNRESERVED,
                CharacterValidationConstants.getCharacterSet(ValidationType.COOKIE_VALUE));
    }

    @Test
    void shouldNotAllowNullValidationType() {
        assertThrows(NullPointerException.class, () ->
                CharacterValidationConstants.getCharacterSet(null));
    }
}
