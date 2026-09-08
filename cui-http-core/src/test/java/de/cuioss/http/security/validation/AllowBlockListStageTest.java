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

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for header-name / content-type allow-block list enforcement (F-08).
 */
@DisplayName("AllowBlockListStage (F-08)")
class AllowBlockListStageTest {

    /** LINE SEPARATOR - a line-forging code point outside the ISO-control range. */
    private static final String LINE_SEPARATOR = Character.toString(0x2028);

    /** PARAGRAPH SEPARATOR - a line-forging code point outside the ISO-control range. */
    private static final String PARAGRAPH_SEPARATOR = Character.toString(0x2029);

    @Test
    @DisplayName("Empty lists allow everything")
    void shouldAllowAllWhenEmpty() {
        var stage = new AllowBlockListStage(Set.of(), Set.of(), ValidationType.HEADER_NAME);
        assertEquals(Optional.of("X-Anything"), stage.validate("X-Anything"));
        assertEquals(Optional.empty(), stage.validate(null));
        assertEquals(Optional.of(""), stage.validate(""));
    }

    @Test
    @DisplayName("Block-list rejects case-insensitively")
    void shouldRejectBlocked() {
        var stage = new AllowBlockListStage(Set.of(), Set.of("X-Debug"), ValidationType.HEADER_NAME);
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate("x-debug"));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("block-listed"));
        // A non-blocked value passes.
        assertEquals(Optional.of("X-Allowed"), stage.validate("X-Allowed"));
    }

    @Test
    @DisplayName("Non-empty allow-list rejects values not in it")
    void shouldEnforceAllowList() {
        var stage = new AllowBlockListStage(Set.of("Accept", "Content-Type"), Set.of(), ValidationType.HEADER_NAME);
        assertEquals(Optional.of("accept"), stage.validate("accept")); // case-insensitive match
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate("X-Custom"));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("allow-list"));
    }

    @Test
    @DisplayName("Non-empty allow-list rejects the empty value")
    void shouldRejectEmptyValueAgainstAllowList() {
        var stage = new AllowBlockListStage(Set.of("Accept"), Set.of(), ValidationType.HEADER_NAME);
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(""));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("allow-list"));
    }

    @Test
    @DisplayName("Block-listed empty value is rejected")
    void shouldRejectEmptyValueAgainstBlockList() {
        var stage = new AllowBlockListStage(Set.of(), Set.of(""), ValidationType.HEADER_NAME);
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(""));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("block-listed"));
    }

    @Test
    @DisplayName("Block-list takes precedence over allow-list")
    void shouldPreferBlockOverAllow() {
        var stage = new AllowBlockListStage(Set.of("X-Debug"), Set.of("X-Debug"), ValidationType.HEADER_NAME);
        assertThrows(UrlSecurityException.class, () -> stage.validate("X-Debug"));
    }

    @Test
    @DisplayName("Header-name pipeline enforces the configured block-list")
    void shouldEnforceInHeaderNamePipeline() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedHeaderNames(Set.of("X-Debug"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createHeaderNamePipeline(config, counter);

        assertThrows(UrlSecurityException.class, () -> pipeline.validate("X-Debug"));
        assertTrue(counter.getCount(UrlSecurityFailureType.INVALID_INPUT) >= 1);
        assertTrue(pipeline.validate("Accept").isPresent());
    }

    @Test
    @DisplayName("Content-type pipeline enforces the configured allow-list")
    void shouldEnforceInContentTypePipeline() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createContentTypePipeline(config, counter);

        assertTrue(pipeline.validate("application/json").isPresent());
        assertThrows(UrlSecurityException.class, () -> pipeline.validate("application/octet-stream"));
    }

    @Test
    @DisplayName("Content-type matching ignores parameters (charset, boundary)")
    void shouldMatchContentTypeMediaTypeIgnoringParameters() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createContentTypePipeline(config, counter);

        // Allowed media type with parameters must still pass (was a false positive before the fix).
        assertTrue(pipeline.validate("application/json; charset=UTF-8").isPresent());
        assertTrue(pipeline.validate("application/json;charset=utf-8").isPresent());
        // Blocked media type with parameters must still be rejected.
        assertThrows(UrlSecurityException.class,
                () -> pipeline.validate("application/octet-stream; name=evil.bin"));
    }

    @Test
    @DisplayName("Block-list rejection escapes a CR/LF payload in the rendered value")
    void shouldEscapeControlCharactersOnBlockListRejection() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String forgingValue = "application/octet-stream; name=evil\r\nX-Injected: yes";

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(forgingValue));

        String detail = exception.getDetail().orElseThrow();
        String message = exception.getMessage();
        assertAll("block-listed value rendered without raw line breaks",
                () -> assertTrue(detail.contains("block-listed"), "the rejection wording is unchanged"),
                () -> assertFalse(detail.contains("\r"), "detail must not carry a raw CR"),
                () -> assertFalse(detail.contains("\n"), "detail must not carry a raw LF"),
                () -> assertTrue(detail.contains("U+000D"), "CR must render as U+000D"),
                () -> assertTrue(detail.contains("U+000A"), "LF must render as U+000A"),
                () -> assertFalse(message.contains("\r"), "message must not carry a raw CR"),
                () -> assertFalse(message.contains("\n"), "message must not carry a raw LF"),
                () -> assertEquals(forgingValue, exception.getOriginalInput(),
                        "the original input is unaltered"));
    }

    @Test
    @DisplayName("Allow-list rejection escapes a CR/LF payload in the rendered value")
    void shouldEscapeControlCharactersOnAllowListRejection() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String forgingValue = "text/html; name=evil\r\nX-Injected: yes";

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(forgingValue));

        String detail = exception.getDetail().orElseThrow();
        String message = exception.getMessage();
        assertAll("allow-list rejected value rendered without raw line breaks",
                () -> assertTrue(detail.contains("allow-list"), "the rejection wording is unchanged"),
                () -> assertFalse(detail.contains("\r"), "detail must not carry a raw CR"),
                () -> assertFalse(detail.contains("\n"), "detail must not carry a raw LF"),
                () -> assertTrue(detail.contains("U+000D"), "CR must render as U+000D"),
                () -> assertTrue(detail.contains("U+000A"), "LF must render as U+000A"),
                () -> assertFalse(message.contains("\r"), "message must not carry a raw CR"),
                () -> assertFalse(message.contains("\n"), "message must not carry a raw LF"),
                () -> assertEquals(forgingValue, exception.getOriginalInput(),
                        "the original input is unaltered"));
    }

    @Test
    @DisplayName("Block-list rejection escapes the Unicode line and paragraph separators")
    void shouldEscapeUnicodeSeparatorsOnBlockListRejection() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String forgingValue = "application/octet-stream; name=evil" + LINE_SEPARATOR
                + "X-Injected: yes" + PARAGRAPH_SEPARATOR + "X-Also-Injected: yes";

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(forgingValue));

        String detail = exception.getDetail().orElseThrow();
        assertAll("block-listed value rendered without raw Unicode separators",
                () -> assertTrue(detail.contains("block-listed"), "the rejection wording is unchanged"),
                () -> assertFalse(detail.contains(LINE_SEPARATOR), "detail must not carry a raw U+2028"),
                () -> assertFalse(detail.contains(PARAGRAPH_SEPARATOR), "detail must not carry a raw U+2029"),
                () -> assertTrue(detail.contains("U+2028"), "LINE SEPARATOR must render as U+2028"),
                () -> assertTrue(detail.contains("U+2029"), "PARAGRAPH SEPARATOR must render as U+2029"),
                () -> assertEquals(forgingValue, exception.getOriginalInput(),
                        "the original input is unaltered"));
    }

    @Test
    @DisplayName("Allow-list rejection escapes the Unicode line and paragraph separators")
    void shouldEscapeUnicodeSeparatorsOnAllowListRejection() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String forgingValue = "text/html; name=evil" + LINE_SEPARATOR
                + "X-Injected: yes" + PARAGRAPH_SEPARATOR + "X-Also-Injected: yes";

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(forgingValue));

        String detail = exception.getDetail().orElseThrow();
        assertAll("allow-list rejected value rendered without raw Unicode separators",
                () -> assertTrue(detail.contains("allow-list"), "the rejection wording is unchanged"),
                () -> assertFalse(detail.contains(LINE_SEPARATOR), "detail must not carry a raw U+2028"),
                () -> assertFalse(detail.contains(PARAGRAPH_SEPARATOR), "detail must not carry a raw U+2029"),
                () -> assertTrue(detail.contains("U+2028"), "LINE SEPARATOR must render as U+2028"),
                () -> assertTrue(detail.contains("U+2029"), "PARAGRAPH SEPARATOR must render as U+2029"),
                () -> assertEquals(forgingValue, exception.getOriginalInput(),
                        "the original input is unaltered"));
    }

    @Test
    @DisplayName("An over-long block-listed value is bounded where the detail is built")
    void shouldBoundRenderedDetailForOverlongBlockListedValue() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String overlongValue = "application/octet-stream; name=" + "A".repeat(5000);

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(overlongValue));

        String rendered = renderedOperandOf(exception.getDetail().orElseThrow(), "' is block-listed");
        assertAll("the rejected value is bounded at the source, not only where it is rendered",
                () -> assertEquals(203, rendered.length(),
                        "200 rendered characters plus the three-character truncation marker"),
                () -> assertTrue(rendered.endsWith("..."), "the cut is marked"),
                () -> assertEquals(203, renderedDetailOfMessage(exception.getMessage()).length(),
                        "getMessage() bounds the already-bounded detail at the same limit"),
                () -> assertEquals(overlongValue, exception.getOriginalInput(),
                        "the original input is unaltered"));
    }

    @Test
    @DisplayName("Control-character escaping cannot amplify the detail past the bound")
    void shouldBoundRenderedDetailForControlCharacterAmplification() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        var stage = AllowBlockListStage.forContentTypes(config);
        String amplifyingValue = "application/octet-stream; name=" + "\r\n".repeat(2000);

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate(amplifyingValue));

        String rendered = renderedOperandOf(exception.getDetail().orElseThrow(), "' is block-listed");
        assertAll("each control code point expands sixfold inside the same bound",
                () -> assertEquals(202, rendered.length(),
                        "the cut is taken before the bound is exceeded, so no partial U+XXXX survives"),
                () -> assertTrue(rendered.endsWith("..."), "the cut is marked"),
                () -> assertTrue(rendered.contains("U+000D"), "CR renders as U+000D"),
                () -> assertFalse(rendered.contains("\r"), "no raw CR survives"),
                () -> assertFalse(rendered.contains("\n"), "no raw LF survives"),
                () -> assertEquals(203, renderedDetailOfMessage(exception.getMessage()).length(),
                        "getMessage() bounds the same operand at the same limit"));
    }

    /**
     * Extracts the rendered value from a rejection detail of the shape
     * {@code Value '<rendered>'<suffix>}.
     */
    private static String renderedOperandOf(String detail, String suffix) {
        assertTrue(detail.endsWith(suffix), "the rejection wording is unchanged");
        return detail.substring("Value '".length(), detail.length() - suffix.length());
    }

    /**
     * Extracts the rendered detail segment from {@code getMessage()} without depending on the
     * failure-type description that precedes it.
     */
    private static String renderedDetailOfMessage(String message) {
        int start = message.indexOf(" - Value '");
        assertTrue(start >= 0, "the message carries the rendered detail");
        int end = message.lastIndexOf(" (input: ");
        assertTrue(end > start, "the message carries the redacted-input suffix");
        return message.substring(start + " - ".length(), end);
    }

    @Test
    @DisplayName("Null arguments are rejected")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(null, Set.of(), ValidationType.HEADER_NAME));
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(Set.of(), null, ValidationType.HEADER_NAME));
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(Set.of(), Set.of(), null));
    }
}
