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
package de.cuioss.http.security.exceptions;

import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link UrlSecurityException}
 */
class UrlSecurityExceptionTest {

    private static final UrlSecurityFailureType TEST_FAILURE_TYPE = UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED;
    private static final ValidationType TEST_VALIDATION_TYPE = ValidationType.URL_PATH;
    private static final String TEST_INPUT = "../../../etc/passwd";
    private static final String TEST_SANITIZED = "etc/passwd";
    private static final String TEST_DETAIL = "Path traversal attempt detected";

    @Test
    void shouldBuildMinimalException() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .build();

        assertEquals(TEST_FAILURE_TYPE, exception.getFailureType());
        assertEquals(TEST_VALIDATION_TYPE, exception.getValidationType());
        assertEquals(TEST_INPUT, exception.getOriginalInput());
        assertTrue(exception.getSanitizedInput().isEmpty());
        assertTrue(exception.getDetail().isEmpty());
        assertNull(exception.getCause());
    }

    @Test
    void shouldBuildFullException() {
        Throwable cause = new IllegalArgumentException("Root cause");

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .sanitizedInput(TEST_SANITIZED)
                .detail(TEST_DETAIL)
                .cause(cause)
                .build();

        assertEquals(TEST_FAILURE_TYPE, exception.getFailureType());
        assertEquals(TEST_VALIDATION_TYPE, exception.getValidationType());
        assertEquals(TEST_INPUT, exception.getOriginalInput());
        assertTrue(exception.getSanitizedInput().isPresent());
        assertEquals(TEST_SANITIZED, exception.getSanitizedInput().get());
        assertTrue(exception.getDetail().isPresent());
        assertEquals(TEST_DETAIL, exception.getDetail().get());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldRequireFailureType() {
        var builder = UrlSecurityException.builder()
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                builder::build);

        assertTrue(thrown.getMessage().contains("failureType must be set"));
    }

    @Test
    void shouldRequireValidationType() {
        var builder = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .originalInput(TEST_INPUT);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                builder::build);

        assertTrue(thrown.getMessage().contains("validationType must be set"));
    }

    @Test
    void shouldRequireOriginalInput() {
        var builder = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                builder::build);

        assertTrue(thrown.getMessage().contains("originalInput must be set"));
    }

    @Test
    void shouldGenerateDescriptiveMessage() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(TEST_DETAIL)
                .build();

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(TEST_VALIDATION_TYPE.toString()));
        assertTrue(message.contains(TEST_FAILURE_TYPE.getDescription()));
        assertTrue(message.contains(TEST_INPUT));
        assertTrue(message.contains(TEST_DETAIL));
    }

    @Test
    void shouldTruncateLongInputInMessage() {
        String longInput = "A".repeat(300);

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(longInput)
                .build();

        String message = exception.getMessage();
        assertNotNull(message);
        assertFalse(message.contains(longInput));
        assertTrue(message.contains("..."));
    }

    @Test
    void shouldSanitizeControlCharactersInMessage() {
        String inputWithControlChars = "test\r\n\ttab\u0000null";

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.CONTROL_CHARACTERS)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(inputWithControlChars)
                .build();

        String message = exception.getMessage();
        assertNotNull(message);
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\t"));
        assertFalse(message.contains("\u0000"));
        assertTrue(message.contains("?"));
    }
}