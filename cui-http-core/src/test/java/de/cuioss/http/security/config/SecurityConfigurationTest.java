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
package de.cuioss.http.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SecurityConfiguration}
 */
class SecurityConfigurationTest {

    @Test
    void shouldCreateConfigurationWithBuilder() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(2048)
                .maxParameterValueLength(512)
                .normalizeUnicode(true)
                .build();

        assertEquals(2048, config.maxPathLength());
        assertEquals(512, config.maxParameterValueLength());
        assertTrue(config.normalizeUnicode());
    }

    @Test
    void shouldCreateStrictConfiguration() {
        SecurityConfiguration config = SecurityConfiguration.strict();

        assertEquals(1024, config.maxPathLength());
        assertFalse(config.allowDoubleEncoding());
        assertFalse(config.allowNullBytes());
        assertFalse(config.allowControlCharacters());
        assertFalse(config.allowExtendedAscii());
        assertTrue(config.normalizeUnicode());
        assertTrue(config.caseSensitiveComparison());
        assertTrue(config.failOnSuspiciousPatterns());
    }

    @Test
    void shouldCreateLenientConfiguration() {
        SecurityConfiguration config = SecurityConfiguration.lenient();

        assertEquals(8192, config.maxPathLength());
        assertTrue(config.allowDoubleEncoding());
        assertFalse(config.allowNullBytes()); // Null bytes are never allowed, even in lenient mode
        assertTrue(config.allowControlCharacters());
        assertTrue(config.allowExtendedAscii());
        assertFalse(config.normalizeUnicode());
        assertFalse(config.failOnSuspiciousPatterns());
    }

    @Test
    void shouldCreateDefaultConfiguration() {
        SecurityConfiguration config = SecurityConfiguration.defaults();

        assertEquals(4096, config.maxPathLength());
        assertFalse(config.allowDoubleEncoding());
        assertFalse(config.allowNullBytes());
        assertFalse(config.allowControlCharacters());
        assertTrue(config.allowExtendedAscii());
        assertFalse(config.failOnSuspiciousPatterns());
    }

    @Test
    void presetFactoriesShouldDelegateToSecurityDefaults() {
        // Single source of truth: factory methods and SecurityDefaults constants
        // must be identical - they were divergent before 1.5
        assertSame(SecurityDefaults.STRICT_CONFIGURATION, SecurityConfiguration.strict());
        assertSame(SecurityDefaults.DEFAULT_CONFIGURATION, SecurityConfiguration.defaults());
        assertSame(SecurityDefaults.LENIENT_CONFIGURATION, SecurityConfiguration.lenient());
    }

    @Test
    void defaultsShouldEqualPlainBuilderResult() {
        assertEquals(SecurityConfiguration.builder().build(), SecurityConfiguration.defaults());
    }

    /**
     * Every length setter rejects non-positive values with a descriptive message.
     * Covers the builder guards; the record constructor guards are covered by
     * {@link #recordConstructorShouldValidateConstraints()}.
     */
    @ParameterizedTest
    @MethodSource("lengthSetters")
    void lengthSettersShouldRejectNonPositiveValues(
            String property, BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder> setter) {
        var builder = SecurityConfiguration.builder();

        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () -> setter.apply(builder, 0));
        assertTrue(zero.getMessage().contains(property + " must be positive"));

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> setter.apply(builder, -10));
        assertTrue(negative.getMessage().contains(property + " must be positive"));
    }

    static Stream<Arguments> lengthSetters() {
        return Stream.of(
                Arguments.of("maxPathLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxPathLength),
                Arguments.of("maxParameterNameLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxParameterNameLength),
                Arguments.of("maxParameterValueLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxParameterValueLength),
                Arguments.of("maxHeaderNameLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxHeaderNameLength),
                Arguments.of("maxHeaderValueLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxHeaderValueLength),
                Arguments.of("maxCookieNameLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxCookieNameLength),
                Arguments.of("maxCookieValueLength",
                        (BiFunction<SecurityConfigurationBuilder, Integer, SecurityConfigurationBuilder>) SecurityConfigurationBuilder::maxCookieValueLength));
    }

    @Test
    void shouldAllowZeroBodySize() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxBodySize(0)
                .build();
        assertEquals(0, config.maxBodySize());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidateNonNegativeBodySize() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxBodySize(-1).build());
        assertTrue(thrown.getMessage().contains("maxBodySize must be non-negative"));
    }

    @Test
    void recordConstructorShouldValidateConstraints() {
        // Direct record construction must enforce the same constraints as the builder;
        // each call violates exactly one guard (path, param name/value, header
        // name/value, cookie name/value lengths, body size)
        assertConstructorRejects(0, 128, 2048, 128, 2048, 128, 2048, 1024);
        assertConstructorRejects(4096, 0, 2048, 128, 2048, 128, 2048, 1024);
        assertConstructorRejects(4096, 128, 0, 128, 2048, 128, 2048, 1024);
        assertConstructorRejects(4096, 128, 2048, 0, 2048, 128, 2048, 1024);
        assertConstructorRejects(4096, 128, 2048, 128, 0, 128, 2048, 1024);
        assertConstructorRejects(4096, 128, 2048, 128, 2048, 0, 2048, 1024);
        assertConstructorRejects(4096, 128, 2048, 128, 2048, 128, 0, 1024);
        assertConstructorRejects(4096, 128, 2048, 128, 2048, 128, 2048, -1);
    }

    @SuppressWarnings("java:S107")
    private static void assertConstructorRejects(int pathLength, int paramNameLength, int paramValueLength,
            int headerNameLength, int headerValueLength, int cookieNameLength, int cookieValueLength, long bodySize) {
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfiguration(
                pathLength, false, paramNameLength, paramValueLength,
                headerNameLength, headerValueLength, cookieNameLength, cookieValueLength,
                bodySize, false, false, true, false, false, false,
                false, false, 100, 50, 20));
    }

    @Test
    void shouldDetectStrictConfiguration() {
        SecurityConfiguration strict = SecurityConfiguration.strict();
        assertTrue(strict.isStrict());
        assertFalse(strict.isLenient());
    }

    @Test
    void shouldDetectLenientConfiguration() {
        SecurityConfiguration lenient = SecurityConfiguration.lenient();
        assertTrue(lenient.isLenient());
        assertFalse(lenient.isStrict());
    }

    @Test
    void defaultConfigurationShouldBeNeitherStrictNorLenient() {
        SecurityConfiguration defaults = SecurityConfiguration.defaults();
        assertFalse(defaults.isStrict());
        assertFalse(defaults.isLenient());
    }

    @Test
    void nearStrictVariantsShouldNotBeStrict() {
        // Each variant flips exactly one of the strict-defining settings
        assertFalse(strictBuilder().allowDoubleEncoding(true).build().isStrict());
        assertFalse(strictBuilder().allowNullBytes(true).build().isStrict());
        assertFalse(strictBuilder().allowControlCharacters(true).build().isStrict());
        assertFalse(strictBuilder().allowExtendedAscii(true).build().isStrict());
        assertFalse(strictBuilder().normalizeUnicode(false).build().isStrict());
        assertFalse(strictBuilder().failOnSuspiciousPatterns(false).build().isStrict());
    }

    @Test
    void nearLenientVariantsShouldNotBeLenient() {
        // Each variant flips exactly one of the lenient-defining settings;
        // in particular, allowing null bytes must disqualify a config from lenient
        assertFalse(lenientBuilder().allowDoubleEncoding(false).build().isLenient());
        assertFalse(lenientBuilder().allowNullBytes(true).build().isLenient());
        assertFalse(lenientBuilder().allowControlCharacters(false).build().isLenient());
        assertFalse(lenientBuilder().allowExtendedAscii(false).build().isLenient());
        assertFalse(lenientBuilder().normalizeUnicode(true).build().isLenient());
        assertFalse(lenientBuilder().failOnSuspiciousPatterns(true).build().isLenient());
    }

    private static SecurityConfigurationBuilder strictBuilder() {
        return SecurityConfiguration.builder()
                .encoding(false, false, false, true)
                .failOnSuspiciousPatterns(true);
    }

    private static SecurityConfigurationBuilder lenientBuilder() {
        return SecurityConfiguration.builder()
                .allowDoubleEncoding(true)
                .encoding(false, true, true, false)
                .failOnSuspiciousPatterns(false);
    }

    @Test
    void shouldSupportEquality() {
        SecurityConfiguration config1 = SecurityConfiguration.builder()
                .maxPathLength(2048)
                .normalizeUnicode(true)
                .build();
        SecurityConfiguration config2 = SecurityConfiguration.builder()
                .maxPathLength(2048)
                .normalizeUnicode(true)
                .build();
        SecurityConfiguration different = SecurityConfiguration.builder()
                .maxPathLength(1024)
                .normalizeUnicode(true)
                .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, different);
    }

    @Test
    void shouldSupportToString() {
        String result = SecurityConfiguration.defaults().toString();

        assertNotNull(result);
        assertTrue(result.contains("maxPathLength"));
        assertTrue(result.contains("4096"));
    }
}
