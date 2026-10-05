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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator.Surface;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterNameValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T19: Test length limit attacks
 *
 * <p>
 * A length limit belongs to one HTTP component, so every length attack is validated against the
 * pipeline whose limit it exceeds, under the strict preset limits:
 * </p>
 * <ul>
 *   <li>an overlong path on {@link URLPathValidationPipeline} -
 *       {@link UrlSecurityFailureType#PATH_TOO_LONG}</li>
 *   <li>an overlong parameter name on {@link URLParameterNameValidationPipeline}, an overlong
 *       parameter value on {@link URLParameterValidationPipeline}, and an overlong header name or
 *       value on {@link HTTPHeaderValidationPipeline} -
 *       {@link UrlSecurityFailureType#INPUT_TOO_LONG}</li>
 * </ul>
 *
 * <p>
 * Every payload consists of characters its surface admits - a path payload in particular carries
 * no {@code ?}, {@code #} or space - so the verdict is the length limit and nothing else.
 * </p>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 3986 - Uniform Resource Identifier (URI): Generic Syntax</li>
 *   <li>RFC 7230 - HTTP/1.1 Message Syntax and Routing</li>
 *   <li>OWASP - Application Denial of Service</li>
 *   <li>CWE-400 - Uncontrolled Resource Consumption</li>
 *   <li>CWE-770 - Allocation of Resources Without Limits or Throttling</li>
 *   <li>CWE-120 - Buffer Copy without Checking Size of Input</li>
 * </ul>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T19: URL Length Limit Attack Tests")
class URLLengthLimitAttackTest {

    /** The verdict of the path pipeline's length stage. */
    private static final UrlSecurityFailureType OVERLONG_PATH = UrlSecurityFailureType.PATH_TOO_LONG;

    /** The verdict of every other pipeline's length stage. */
    private static final UrlSecurityFailureType OVERLONG_COMPONENT = UrlSecurityFailureType.INPUT_TOO_LONG;

    private SecurityEventCounter eventCounter;
    private URLPathValidationPipeline pathPipeline;
    private URLParameterNameValidationPipeline parameterNamePipeline;
    private URLParameterValidationPipeline parameterValuePipeline;
    private HTTPHeaderValidationPipeline headerNamePipeline;
    private HTTPHeaderValidationPipeline headerValuePipeline;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(SecurityDefaults.MAX_PATH_LENGTH_STRICT)
                .maxParameterNameLength(SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_STRICT)
                .maxParameterValueLength(SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_STRICT)
                .maxHeaderNameLength(SecurityDefaults.MAX_HEADER_NAME_LENGTH_STRICT)
                .maxHeaderValueLength(SecurityDefaults.MAX_HEADER_VALUE_LENGTH_STRICT)
                .build();
        eventCounter = new SecurityEventCounter();
        pathPipeline = new URLPathValidationPipeline(config, eventCounter);
        parameterNamePipeline = new URLParameterNameValidationPipeline(config, eventCounter);
        parameterValuePipeline = new URLParameterValidationPipeline(config, eventCounter);
        headerNamePipeline = new HTTPHeaderValidationPipeline(config, eventCounter, ValidationType.HEADER_NAME);
        headerValuePipeline = new HTTPHeaderValidationPipeline(config, eventCounter, ValidationType.HEADER_VALUE);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.class, count = 40)
    @DisplayName("Overlong paths are rejected by the path pipeline as PATH_TOO_LONG")
    void shouldRejectOverlongPath(String overlongPath) {
        assertRejected(pathPipeline, overlongPath, OVERLONG_PATH);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForParameterName.class, count = 40)
    @DisplayName("Overlong parameter names are rejected by the parameter-name pipeline as INPUT_TOO_LONG")
    void shouldRejectOverlongParameterName(String overlongParameterName) {
        assertRejected(parameterNamePipeline, overlongParameterName, OVERLONG_COMPONENT);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForParameterValue.class, count = 40)
    @DisplayName("Overlong parameter values are rejected by the parameter pipeline as INPUT_TOO_LONG")
    void shouldRejectOverlongParameterValue(String overlongParameterValue) {
        assertRejected(parameterValuePipeline, overlongParameterValue, OVERLONG_COMPONENT);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForHeaderName.class, count = 40)
    @DisplayName("Overlong header names are rejected by the header-name pipeline as INPUT_TOO_LONG")
    void shouldRejectOverlongHeaderName(String overlongHeaderName) {
        assertRejected(headerNamePipeline, overlongHeaderName, OVERLONG_COMPONENT);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForHeaderValue.class, count = 40)
    @DisplayName("Overlong header values are rejected by the header-value pipeline as INPUT_TOO_LONG")
    void shouldRejectOverlongHeaderValue(String overlongHeaderValue) {
        assertRejected(headerValuePipeline, overlongHeaderValue, OVERLONG_COMPONENT);
    }

    @ParameterizedTest
    @EnumSource(Surface.class)
    @DisplayName("A value one character over the limit is rejected for its length")
    void shouldRejectValueOneOverLimit(Surface surface) {
        String oneOverLimit = legalValueOfLength(surface, surface.strictLimit() + 1);

        assertRejected(pipelineFor(surface), oneOverLimit, expectedFailureFor(surface));
    }

    @ParameterizedTest
    @EnumSource(Surface.class)
    @DisplayName("A value of exactly the limit is accepted unchanged")
    void shouldAcceptValueAtLimit(Surface surface) {
        String atLimit = legalValueOfLength(surface, surface.strictLimit());

        Optional<String> validated = assertDoesNotThrow(() -> pipelineFor(surface).validate(atLimit));

        assertEquals(Optional.of(atLimit), validated, "A value at the limit is not a length attack");
        assertEquals(0, eventCounter.getTotalCount(), "An accepted value records no security event");
    }

    @Test
    @DisplayName("The path limit is measured on the wire form, so percent-encoding does not shorten a path")
    void shouldMeasurePathLengthOnWireForm() {
        String encodedPath = "/" + "%41".repeat(342);

        assertRejected(pathPipeline, encodedPath, OVERLONG_PATH);
    }

    private HttpSecurityValidator pipelineFor(Surface surface) {
        return switch (surface) {
            case URL_PATH -> pathPipeline;
            case PARAMETER_NAME -> parameterNamePipeline;
            case PARAMETER_VALUE -> parameterValuePipeline;
            case HEADER_NAME -> headerNamePipeline;
            case HEADER_VALUE -> headerValuePipeline;
        };
    }

    private static UrlSecurityFailureType expectedFailureFor(Surface surface) {
        return surface == Surface.URL_PATH ? OVERLONG_PATH : OVERLONG_COMPONENT;
    }

    /**
     * Builds a value of exactly {@code length} characters that its surface admits: letters, and a
     * leading {@code /} for a path.
     */
    private static String legalValueOfLength(Surface surface, int length) {
        return surface == Surface.URL_PATH
                ? "/" + Generators.letterStrings(length - 1, length - 1).next()
                : Generators.letterStrings(length, length).next();
    }

    private void assertRejected(HttpSecurityValidator pipeline, String attack, UrlSecurityFailureType expected) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Length attack of " + attack.length() + " characters should be rejected");

        assertEquals(expected, exception.getFailureType(),
                () -> "Unexpected verdict for a value of " + attack.length() + " characters");
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }
}
