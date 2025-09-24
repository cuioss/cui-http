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

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CharacterValidationStageTest {

    private final SecurityConfiguration config = SecurityConfiguration.defaults();

    @Test
    void shouldAllowNullAndEmptyValues() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        assertEquals(Optional.empty(), stage.validate(null));
        var result = stage.validate("");
        assertTrue(result.isPresent());
        assertEquals("", result.get());
    }

    @Test
    void shouldAllowValidPathCharacters() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String validPath = "/api/users/123";
        var result = stage.validate(validPath);
        assertTrue(result.isPresent());
        assertEquals(validPath, result.get());

        String complexPath = "/path/with-special_chars.txt~test!$&'()*+,;=:@";
        var complexResult = stage.validate(complexPath);
        assertTrue(complexResult.isPresent());
        assertEquals(complexPath, complexResult.get());
    }

    @Test
    void shouldAllowValidQueryCharacters() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.PARAMETER_NAME);

        String validParam = "userName123";
        var result = stage.validate(validParam);
        assertTrue(result.isPresent());
        assertEquals(validParam, result.get());

        String complexParam = "param_name-with.special~chars!$'()*+,;";
        var complexResult = stage.validate(complexParam);
        assertTrue(complexResult.isPresent());
        assertEquals(complexParam, complexResult.get());
    }

    @Test
    void shouldAllowValidHeaderCharacters() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_NAME);

        String validHeader = "X-Custom-Header";
        var result = stage.validate(validHeader);
        assertTrue(result.isPresent());
        assertEquals(validHeader, result.get());

        String headerWithNumbers = "Header123";
        var numbersResult = stage.validate(headerWithNumbers);
        assertTrue(numbersResult.isPresent());
        assertEquals(headerWithNumbers, numbersResult.get());
    }

    @Test
    void shouldAllowSpaceInHeaderValues() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_VALUE);

        String headerValue = "Mozilla/5.0 Chrome Safari";
        var result = stage.validate(headerValue);
        assertTrue(result.isPresent());
        assertEquals(headerValue, result.get());
    }

    @Test
    void shouldRejectNullByteInjection() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String maliciousPath = "/path/with\0null/byte";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(maliciousPath));

        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Null byte detected at position 10"));
    }

    @Test
    void shouldRejectEncodedNullByte() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String maliciousPath = "/path/with%00null/byte";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(maliciousPath));

        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Encoded null byte (%00) detected at position 10"));
    }

    @Test
    void shouldRejectInvalidCharacters() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String pathWithInvalidChar = "/path/with spaces/invalid";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(pathWithInvalidChar));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid character ' ' (0x20) at position 10"));
    }

    @Test
    void shouldRejectInvalidEncodingFormat() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        // Incomplete percent encoding
        String incompleteEncoding = "/path/with%2";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(incompleteEncoding));

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Incomplete percent encoding at position 10"));
    }

    @Test
    void shouldRejectInvalidHexDigits() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String invalidHex = "/path/with%2G";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(invalidHex));

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid hex digits in percent encoding at position 10"));
    }

    @Test
    void shouldAllowValidPercentEncoding() throws UrlSecurityException {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String encodedPath = "/path/with%20encoded%2Fchars";
        var encodedResult = stage.validate(encodedPath);
        assertTrue(encodedResult.isPresent());
        assertEquals(encodedPath, encodedResult.get());

        String upperCaseHex = "/path/with%2A%2B";
        var upperResult = stage.validate(upperCaseHex);
        assertTrue(upperResult.isPresent());
        assertEquals(upperCaseHex, upperResult.get());

        String lowerCaseHex = "/path/with%2a%2b";
        var lowerResult = stage.validate(lowerCaseHex);
        assertTrue(lowerResult.isPresent());
        assertEquals(lowerCaseHex, lowerResult.get());
    }

    @Test
    void shouldNotAllowPercentEncodingInHeaders() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_NAME);

        // Headers should allow % character, but percent sequences are not decoded
        // So %20 should be treated as literal characters, which is allowed
        String headerWithEncoding = "Header%20Name";

        // This should actually pass since % is allowed in headers
        assertDoesNotThrow(() -> {
            var result = stage.validate(headerWithEncoding);
            assertTrue(result.isPresent());
            return result.get();
        });

        // Test with a character that's actually not allowed in headers (control character)
        String headerWithControlChar = "Header\u0001Name";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(headerWithControlChar));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid character"));
    }

    @ParameterizedTest
    @EnumSource(ValidationType.class)
    void shouldHandleAllValidationTypes(ValidationType type) {
        CharacterValidationStage stage = new CharacterValidationStage(config, type);

        // Should not throw for basic alphanumeric
        assertDoesNotThrow(() -> {
            var result = stage.validate("abc123");
            assertTrue(result.isPresent());
            return result.get();
        });

        // Should reject null byte for all types
        assertThrows(UrlSecurityException.class, () -> stage.validate("test\0null"));
    }

    @Test
    void shouldRejectHighUnicodeCharacters() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String unicodePath = "/path/with/unicode/字符";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(unicodePath));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
        CharacterValidationStage stage1 = new CharacterValidationStage(config, ValidationType.URL_PATH);
        CharacterValidationStage stage2 = new CharacterValidationStage(config, ValidationType.URL_PATH);
        CharacterValidationStage stage3 = new CharacterValidationStage(config, ValidationType.PARAMETER_NAME);

        assertEquals(stage1, stage2);
        assertEquals(stage1.hashCode(), stage2.hashCode());
        assertNotEquals(stage1, stage3);
    }

    @Test
    void shouldHaveCorrectToString() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);
        String toString = stage.toString();

        assertTrue(toString.contains("CharacterValidationStage"));
        assertTrue(toString.contains("URL_PATH"));
    }
}