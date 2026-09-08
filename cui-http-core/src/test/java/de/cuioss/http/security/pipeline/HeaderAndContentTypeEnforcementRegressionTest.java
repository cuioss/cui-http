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
package de.cuioss.http.security.pipeline;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.http.security.validation.LengthValidationStage;
import de.cuioss.http.security.validation.NormalizationStage;
import de.cuioss.http.security.validation.PatternMatchingStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-cutting, pipeline-level regression tests for the four header and content-type
 * enforcement gaps corrected by this change.
 *
 * <p>Every assertion here goes through a {@link PipelineFactory}-built pipeline rather than
 * through an individual stage, so each test reproduces the gap from a caller's angle: it fails
 * against the pre-fix code and passes after. The four behaviours pinned are:</p>
 * <ol>
 *   <li>the header-name pipeline enforces the RFC 7230 {@code tchar} set, so a name carrying a
 *       space or a colon is rejected while a valid token name passes;</li>
 *   <li>a non-empty allow-list rejects the empty value like any other value, for both the
 *       content-type and the header-name pipeline;</li>
 *   <li>the header-value pipeline still rejects CR/LF injection after the removal of the two
 *       pass-through stages, and its stage list contains neither of them;</li>
 *   <li>the content-type pipeline applies the header-value length and character checks ahead of
 *       its allow/block lists, so an overlong value and a CR/LF-carrying value are both rejected
 *       even though their media type is allow-listed.</li>
 * </ol>
 */
@DisplayName("Header and content-type enforcement regressions")
class HeaderAndContentTypeEnforcementRegressionTest {

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    private List<HttpSecurityValidator> stagesOf(HttpSecurityValidator pipeline) {
        return assertInstanceOf(AbstractValidationPipeline.class, pipeline,
                "Factory-built pipelines expose their stages via AbstractValidationPipeline").getStages();
    }

    @Nested
    @DisplayName("(1) header names are restricted to the RFC 7230 tchar set")
    class HeaderNameTokenEnforcement {

        private HttpSecurityValidator pipeline() {
            return PipelineFactory.createHeaderNamePipeline(SecurityConfiguration.defaults(), eventCounter);
        }

        @Test
        @DisplayName("rejects a header name containing a space with INVALID_CHARACTER")
        void rejectsSpaceInHeaderName() {
            HttpSecurityValidator pipeline = pipeline();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("X-Foo bar"));

            assertAll("space is not a tchar",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_NAME, exception.getValidationType()),
                    () -> assertEquals("X-Foo bar", exception.getOriginalInput()),
                    () -> assertTrue(eventCounter.getCount(UrlSecurityFailureType.INVALID_CHARACTER) >= 1,
                            "Security event should be recorded"));
        }

        @Test
        @DisplayName("rejects a header name containing a colon with INVALID_CHARACTER")
        void rejectsColonInHeaderName() {
            HttpSecurityValidator pipeline = pipeline();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("X-Foo:"));

            assertAll("colon is a delimiter, not a tchar",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_NAME, exception.getValidationType()),
                    () -> assertEquals("X-Foo:", exception.getOriginalInput()));
        }

        @Test
        @DisplayName("accepts a valid tchar header name unchanged")
        void acceptsValidTokenName() {
            assertEquals(Optional.of("X-Correlation-Id"), pipeline().validate("X-Correlation-Id"));
        }
    }

    @Nested
    @DisplayName("(2) a non-empty allow-list rejects the empty value")
    class EmptyValueAgainstAllowList {

        @Test
        @DisplayName("content-type pipeline rejects the empty string with INVALID_INPUT")
        void contentTypePipelineRejectsEmptyValue() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("application/json"))
                    .build();
            HttpSecurityValidator pipeline = PipelineFactory.createContentTypePipeline(config, eventCounter);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(""));

            assertAll("empty value is evaluated against the allow-list like any other value",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_VALUE, exception.getValidationType()),
                    () -> assertEquals("", exception.getOriginalInput()));
        }

        @Test
        @DisplayName("header-name pipeline rejects the empty string with INVALID_INPUT")
        void headerNamePipelineRejectsEmptyValue() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedHeaderNames(Set.of("Accept"))
                    .build();
            HttpSecurityValidator pipeline = PipelineFactory.createHeaderNamePipeline(config, eventCounter);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(""));

            assertAll("the empty name reaches the allow/block-list stage and is rejected there",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_NAME, exception.getValidationType()),
                    () -> assertEquals("", exception.getOriginalInput()));
        }

        @Test
        @DisplayName("an allow-listed value still passes, so the rejection is list-driven")
        void allowListedValueStillPasses() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedHeaderNames(Set.of("Accept"))
                    .build();
            HttpSecurityValidator pipeline = PipelineFactory.createHeaderNamePipeline(config, eventCounter);

            assertEquals(Optional.of("Accept"), pipeline.validate("Accept"));
        }
    }

    @Nested
    @DisplayName("(3) header values reject CR/LF after the two-stage removal")
    class HeaderValueCrlfAfterStageRemoval {

        private HttpSecurityValidator pipeline() {
            return PipelineFactory.createHeaderValuePipeline(SecurityConfiguration.defaults(), eventCounter);
        }

        @Test
        @DisplayName("rejects CR/LF header injection with INVALID_CHARACTER")
        void rejectsCrlfInjection() {
            HttpSecurityValidator pipeline = pipeline();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("value\r\nX-Injected: 1"));

            assertAll("CR/LF rejection does not depend on the removed stages",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_VALUE, exception.getValidationType()),
                    () -> assertEquals("value\r\nX-Injected: 1", exception.getOriginalInput()));
        }

        @Test
        @DisplayName("stage list contains neither a PatternMatchingStage nor a NormalizationStage")
        void stageListOmitsBothRemovedStages() {
            List<HttpSecurityValidator> stages = stagesOf(pipeline());

            assertAll("both stages were pass-throughs for header types and are gone",
                    () -> assertTrue(stages.stream().noneMatch(PatternMatchingStage.class::isInstance),
                            "Header-value pipeline must not contain a PatternMatchingStage"),
                    () -> assertTrue(stages.stream().noneMatch(NormalizationStage.class::isInstance),
                            "Header-value pipeline must not contain a NormalizationStage"));
        }
    }

    @Nested
    @DisplayName("(4) the content-type pipeline applies the header-value length and character checks before its lists")
    class ContentTypeFullEnforcement {

        private static final String ALLOWED_MEDIA_TYPE = "application/json";

        private HttpSecurityValidator pipeline() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of(ALLOWED_MEDIA_TYPE))
                    .build();
            return PipelineFactory.createContentTypePipeline(config, eventCounter);
        }

        /**
         * A pipeline over the same allow-list, carrying the settings the {@code lenient()} preset
         * relaxes that could plausibly weaken the two checks below - the control-character and
         * extended-ASCII permissions and the larger header-value limit. Both settings are read off
         * {@link SecurityConfiguration#lenient()} rather than hard-coded, so this pipeline cannot
         * drift away from the preset it stands for. Per ADR-0017 the rejections must hold even
         * here, at the most permissive configuration.
         */
        private HttpSecurityValidator lenientPipeline() {
            SecurityConfiguration lenient = SecurityConfiguration.lenient();
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of(ALLOWED_MEDIA_TYPE))
                    .allowControlCharacters(lenient.allowControlCharacters())
                    .allowExtendedAscii(lenient.allowExtendedAscii())
                    .maxHeaderValueLength(lenient.maxHeaderValueLength())
                    .build();
            return PipelineFactory.createContentTypePipeline(config, eventCounter);
        }

        @Test
        @DisplayName("rejects an allow-listed media type whose parameters exceed the header-value limit")
        void rejectsValueOverTheHeaderValueLimit() {
            // Longer than the lenient limit, so the same value is overlong under both presets.
            String overlongValue = ALLOWED_MEDIA_TYPE + "; boundary="
                    + syntheticToken(SecurityDefaults.MAX_HEADER_VALUE_LENGTH_LENIENT + 100);

            UrlSecurityException underDefaults = assertThrows(UrlSecurityException.class,
                    () -> pipeline().validate(overlongValue));
            UrlSecurityException underLenient = assertThrows(UrlSecurityException.class,
                    () -> lenientPipeline().validate(overlongValue));

            assertAll("the length stage cuts the value before the list stage sees its media type",
                    () -> assertEquals(UrlSecurityFailureType.INPUT_TOO_LONG, underDefaults.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_VALUE, underDefaults.getValidationType()),
                    () -> assertEquals(UrlSecurityFailureType.INPUT_TOO_LONG, underLenient.getFailureType()),
                    () -> assertEquals(ValidationType.HEADER_VALUE, underLenient.getValidationType()));
        }

        @Test
        @DisplayName("rejects CR/LF smuggled into the parameters of an allow-listed media type")
        void rejectsCrlfInParametersOfAnAllowListedMediaType() {
            // Media-type-only matching strips everything from the first ';', so the allow/block-list
            // stage alone would have accepted this value: the character stage is what rejects it.
            String injected = ALLOWED_MEDIA_TYPE + "; x\r\nX: y";

            UrlSecurityException underDefaults = assertThrows(UrlSecurityException.class,
                    () -> pipeline().validate(injected));
            UrlSecurityException underLenient = assertThrows(UrlSecurityException.class,
                    () -> lenientPipeline().validate(injected));

            assertAll("CR/LF is rejected unconditionally for header types (ADR-0017)",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, underDefaults.getFailureType()),
                    () -> assertEquals(injected, underDefaults.getOriginalInput()),
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, underLenient.getFailureType()),
                    () -> assertEquals(injected, underLenient.getOriginalInput()));
        }

        @Test
        @DisplayName("still rejects a media type absent from the allow-list")
        void stillEnforcesTheAllowList() {
            HttpSecurityValidator pipeline = pipeline();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("application/octet-stream"));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        }

        @Test
        @DisplayName("stage list carries a LengthValidationStage ahead of a CharacterValidationStage")
        void stageListCarriesLengthAndCharacterStages() {
            List<HttpSecurityValidator> stages = stagesOf(pipeline());

            assertAll("the two stages the pipeline previously omitted are now wired",
                    () -> assertInstanceOf(LengthValidationStage.class, stages.getFirst(),
                            "Content-type pipeline must run a LengthValidationStage first"),
                    () -> assertInstanceOf(CharacterValidationStage.class, stages.get(1),
                            "Content-type pipeline must run a CharacterValidationStage second"));
        }

        /**
         * Builds a varied token of the requested length without {@code String.repeat}, matching the
         * long-value construction style used by the sibling pipeline tests.
         */
        private String syntheticToken(int length) {
            StringBuilder token = new StringBuilder(length);
            String alphabet = "abcdefghij";
            for (int i = 0; i < length; i++) {
                token.append(alphabet.charAt(i % alphabet.length()));
            }
            return token.toString();
        }
    }
}
