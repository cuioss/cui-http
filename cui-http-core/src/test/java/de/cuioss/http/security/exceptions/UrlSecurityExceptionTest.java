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

    /** NUL (U+0000). Held as a named constant so no invisible byte appears in this source file. */
    private static final String NUL = String.valueOf((char) 0x0000);
    /** NEL (U+0085), a C1 control that terminates a line in common log viewers. */
    private static final String NEXT_LINE = String.valueOf((char) 0x0085);
    /** LINE SEPARATOR (U+2028), a line terminator for JSON-lines consumers and JavaScript. */
    private static final String LINE_SEPARATOR = String.valueOf((char) 0x2028);

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

        NullPointerException thrown = assertThrows(NullPointerException.class,
                builder::build);

        assertTrue(thrown.getMessage().contains("failureType"));
    }

    @Test
    void shouldHandleNullValidationType() {
        var builder = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .originalInput(TEST_INPUT);

        // Should not throw - null validationType is handled gracefully
        UrlSecurityException exception = assertDoesNotThrow(builder::build);
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    void shouldHandleNullOriginalInput() {
        var builder = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE);

        // Should not throw - null originalInput is handled gracefully
        UrlSecurityException exception = assertDoesNotThrow(builder::build);
        assertNotNull(exception);
        assertTrue(exception.getMessage().endsWith("(input: <redacted, null>)"));
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
        assertFalse(message.contains(TEST_INPUT), "the input is described, never reproduced");
        assertTrue(message.endsWith("(input: <redacted, length=%d>)".formatted(TEST_INPUT.length())));
        assertTrue(message.contains(TEST_DETAIL));
    }

    @Test
    void shouldReportLengthOnlyForLongInputInMessage() {
        String longInput = "A".repeat(300);

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(longInput)
                .build();

        String message = exception.getMessage();
        assertNotNull(message);
        assertFalse(message.contains(longInput));
        assertFalse(message.contains("..."),
                "the input path renders no content, so there is nothing to truncate");
        assertTrue(message.endsWith("(input: <redacted, length=300>)"));
    }

    @Test
    void shouldSanitizeControlCharactersInDetailRendering() {
        String detailWithControlChars = "test\r\n\ttab" + NUL + "null";

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.CONTROL_CHARACTERS)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(detailWithControlChars)
                .build();

        String message = exception.getMessage();
        String rendered = exception.toString();
        assertAll("control characters neutralised on both rendering paths",
                () -> assertFalse(message.contains("\r"), "message must not carry a raw CR"),
                () -> assertFalse(message.contains("\n"), "message must not carry a raw LF"),
                () -> assertFalse(message.contains("\t"), "message must not carry a raw tab"),
                () -> assertFalse(message.contains(NUL), "message must not carry a raw NUL"),
                () -> assertTrue(message.contains("testU+000DU+000AU+0009tabU+0000null"),
                        "the message path escapes each control character"),
                () -> assertFalse(rendered.contains("\r"), "toString must not carry a raw CR"),
                () -> assertFalse(rendered.contains("\n"), "toString must not carry a raw LF"),
                () -> assertFalse(rendered.contains("\t"), "toString must not carry a raw tab"),
                () -> assertFalse(rendered.contains(NUL), "toString must not carry a raw NUL"),
                () -> assertTrue(rendered.contains("detail='test???tab?null'"),
                        "the toString path replaces each control character"));
    }

    @Test
    void shouldEscapeControlCharactersFromDetailOnMessagePath() {
        String detailWithLineBreaks = "Value 'application/octet-stream\r\nX-Injected: evil' is block-listed";

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(detailWithLineBreaks)
                .build();

        String message = exception.getMessage();
        assertAll("detail rendered on the message path",
                () -> assertFalse(message.contains("\r"), "message must not carry a raw CR"),
                () -> assertFalse(message.contains("\n"), "message must not carry a raw LF"),
                () -> assertTrue(message.contains("U+000D"), "CR must render as U+000D"),
                () -> assertTrue(message.contains("U+000A"), "LF must render as U+000A"),
                () -> assertEquals(detailWithLineBreaks, exception.getDetail().orElseThrow(),
                        "the stored detail is unaltered"));
    }

    @Test
    void shouldNeutraliseNextLineCharacterOnBothRenderingPaths() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail("line" + NEXT_LINE + "break")
                .build();

        assertAll("NEL neutralised on both rendering paths",
                () -> assertEquals("Security validation failed [%s]: %s - lineU+0085break (input: <redacted, length=%d>)"
                        .formatted(TEST_VALIDATION_TYPE, TEST_FAILURE_TYPE.getDescription(), TEST_INPUT.length()),
                        exception.getMessage()),
                () -> assertEquals("UrlSecurityException{failureType=%s, validationType=%s, "
                        .formatted(TEST_FAILURE_TYPE, TEST_VALIDATION_TYPE)
                        + "originalInput=<redacted, length=%d>, ".formatted(TEST_INPUT.length())
                        + "sanitizedInput='<redacted, null>', detail='line?break', cause=null}",
                        exception.toString()));
    }

    @Test
    void shouldNeutraliseLineSeparatorCharacterOnBothRenderingPaths() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail("line" + LINE_SEPARATOR + "break")
                .build();

        assertAll("LINE SEPARATOR neutralised on both rendering paths",
                () -> assertEquals("Security validation failed [%s]: %s - lineU+2028break (input: <redacted, length=%d>)"
                        .formatted(TEST_VALIDATION_TYPE, TEST_FAILURE_TYPE.getDescription(), TEST_INPUT.length()),
                        exception.getMessage()),
                () -> assertEquals("UrlSecurityException{failureType=%s, validationType=%s, "
                        .formatted(TEST_FAILURE_TYPE, TEST_VALIDATION_TYPE)
                        + "originalInput=<redacted, length=%d>, ".formatted(TEST_INPUT.length())
                        + "sanitizedInput='<redacted, null>', detail='line?break', cause=null}",
                        exception.toString()));
    }

    @Test
    void shouldBoundOverLongDetailOnMessagePathToTheSameLimitAsToString() {
        String overLongDetail = "B".repeat(300);

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(overLongDetail)
                .build();

        String expectedDetail = "B".repeat(200) + "...";
        assertAll("both rendering paths bound the detail at the same limit with the same marker",
                () -> assertEquals("Security validation failed [%s]: %s - %s (input: <redacted, length=%d>)"
                        .formatted(TEST_VALIDATION_TYPE, TEST_FAILURE_TYPE.getDescription(),
                                expectedDetail, TEST_INPUT.length()),
                        exception.getMessage()),
                () -> assertTrue(exception.toString().contains("detail='%s'".formatted(expectedDetail)),
                        "toString renders the same bounded detail"),
                () -> assertEquals(overLongDetail, exception.getDetail().orElseThrow(),
                        "the stored detail is unaltered"));
    }

    @Test
    void shouldBoundEscapedDetailSoControlCharactersCannotAmplifyTheMessage() {
        String overLongControlDetail = "x" + "\r".repeat(300);

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.CONTROL_CHARACTERS)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(overLongControlDetail)
                .build();

        // The leading 'x' plus 33 whole U+000D sequences occupy 199 characters; a 34th would exceed
        // the 200-character limit, so the cut falls on a sequence boundary and leaves no partial escape.
        String expectedDetail = "x" + "U+000D".repeat(33) + "...";
        assertAll("escaping cannot amplify the message beyond the bound",
                () -> assertEquals("Security validation failed [%s]: %s - %s (input: <redacted, length=%d>)"
                        .formatted(TEST_VALIDATION_TYPE, UrlSecurityFailureType.CONTROL_CHARACTERS.getDescription(),
                                expectedDetail, TEST_INPUT.length()),
                        exception.getMessage()),
                () -> assertEquals(202, expectedDetail.length(),
                        "199 characters of whole rendered characters plus the 3-character marker"),
                () -> assertFalse(exception.getMessage().contains("\r"),
                        "message must not carry a raw CR"));
    }

    @Test
    void shouldNotReproduceCredentialMaterialOnEitherRenderingPath() {
        String bearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                .validationType(ValidationType.HEADER_VALUE)
                .originalInput(bearerToken)
                .build();

        String message = exception.getMessage();
        String rendered = exception.toString();
        assertAll("credential material is never reproduced",
                () -> assertFalse(message.contains(bearerToken), "getMessage must not echo the token"),
                () -> assertFalse(rendered.contains(bearerToken), "toString must not echo the token"),
                () -> assertFalse(message.contains("eyJhbGciOiJIUzI1NiJ9"),
                        "getMessage must not echo the token header"),
                () -> assertFalse(rendered.contains("eyJhbGciOiJIUzI1NiJ9"),
                        "toString must not echo the token header"),
                () -> assertFalse(message.contains(".payload.signature"),
                        "getMessage must not echo the token payload or signature"),
                () -> assertFalse(rendered.contains(".payload.signature"),
                        "toString must not echo the token payload or signature"),
                () -> assertTrue(message.endsWith("(input: <redacted, length=%d>)".formatted(bearerToken.length())),
                        "the message reports the length only"),
                () -> assertEquals(bearerToken, exception.getOriginalInput(),
                        "the accessor still returns the raw value"));
    }

    @Test
    void shouldNotReproduceCredentialMaterialFromSanitizedInput() {
        String bearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";
        String sanitizedToken = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature-stripped";

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                .validationType(ValidationType.HEADER_VALUE)
                .originalInput(bearerToken)
                .sanitizedInput(sanitizedToken)
                .build();

        String message = exception.getMessage();
        String rendered = exception.toString();
        assertAll("the sanitized rendering reproduces no credential material either",
                () -> assertFalse(message.contains("eyJhbGciOiJIUzI1NiJ9"),
                        "getMessage must not echo the token header"),
                () -> assertFalse(rendered.contains("eyJhbGciOiJIUzI1NiJ9"),
                        "toString must not echo the token header"),
                () -> assertFalse(rendered.contains(".payload.signature"),
                        "toString must not echo the token payload or signature"),
                () -> assertTrue(rendered.contains("sanitizedInput='<redacted, length=%d>'"
                                .formatted(sanitizedToken.length())),
                        "toString reports the sanitized length only"),
                () -> assertEquals(sanitizedToken, exception.getSanitizedInput().orElseThrow(),
                        "the accessor still returns the raw value"));
    }

    @Test
    void shouldDistinguishNullInputFromEmptyInput() {
        UrlSecurityException nullInput = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .build();
        UrlSecurityException emptyInput = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput("")
                .build();

        assertAll("a null input stays distinguishable from an empty one",
                () -> assertTrue(nullInput.getMessage().endsWith("(input: <redacted, null>)")),
                () -> assertTrue(emptyInput.getMessage().endsWith("(input: <redacted, length=0>)")));
    }

    @Test
    void toStringShouldIncludeFields() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .build();

        String result = exception.toString();
        assertTrue(result.contains("UrlSecurityException"));
        assertTrue(result.contains(TEST_FAILURE_TYPE.toString()));
        assertTrue(result.contains(TEST_VALIDATION_TYPE.toString()));
        assertFalse(result.contains(TEST_INPUT), "the input is described, never reproduced");
        assertTrue(result.contains("originalInput=<redacted, length=%d>".formatted(TEST_INPUT.length())));
        assertTrue(result.contains("detail='null'"));
    }

    @Test
    void toStringShouldIncludeDetailWhenPresent() {
        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(TEST_DETAIL)
                .build();

        String result = exception.toString();
        assertTrue(result.contains("UrlSecurityException"));
        assertTrue(result.contains(TEST_DETAIL));
        assertTrue(result.contains("detail='"));
        assertFalse(result.contains("detail='null'"));
    }

    @Test
    void toStringShouldTruncateLongDetail() {
        String longDetail = "B".repeat(300);

        UrlSecurityException exception = UrlSecurityException.builder()
                .failureType(TEST_FAILURE_TYPE)
                .validationType(TEST_VALIDATION_TYPE)
                .originalInput(TEST_INPUT)
                .detail(longDetail)
                .build();

        String result = exception.toString();
        assertTrue(result.contains("detail='"));
        assertFalse(result.contains(longDetail));
        assertTrue(result.contains("..."));
    }
}
