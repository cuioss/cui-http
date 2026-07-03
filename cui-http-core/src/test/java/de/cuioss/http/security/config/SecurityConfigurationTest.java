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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SecurityConfiguration}
 */
@EnableGeneratorController
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

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 5)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositivePathLength(Integer invalidValue) {
        var builder = SecurityConfiguration.builder();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                builder.maxPathLength(invalidValue));
        assertTrue(thrown.getMessage().contains("maxPathLength must be positive"));
    }

    static class InvalidPositiveIntegerGenerator implements TypedGenerator<Integer> {
        private final TypedGenerator<Integer> gen = Generators.fixedValues(Integer.class, 0, -1, -100, -999, -1000000);

        @Override
        public Integer next() {
            return gen.next();
        }

        @Override
        public Class<Integer> getType() {
            return Integer.class;
        }
    }

    static class NegativeIntegerGenerator implements TypedGenerator<Integer> {
        private final TypedGenerator<Integer> gen = Generators.fixedValues(Integer.class, -1, -10, -100, -999, -1000000);

        @Override
        public Integer next() {
            return gen.next();
        }

        @Override
        public Class<Integer> getType() {
            return Integer.class;
        }
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveParameterNameLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxParameterNameLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxParameterNameLength must be positive"));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveParameterValueLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxParameterValueLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxParameterValueLength must be positive"));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveHeaderNameLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxHeaderNameLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxHeaderNameLength must be positive"));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveHeaderValueLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxHeaderValueLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxHeaderValueLength must be positive"));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveCookieNameLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxCookieNameLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxCookieNameLength must be positive"));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidPositiveIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidatePositiveCookieValueLength(Integer invalidValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxCookieValueLength(invalidValue).build());
        assertTrue(thrown.getMessage().contains("maxCookieValueLength must be positive"));
    }

    @Test
    void shouldAllowZeroBodySize() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxBodySize(0)
                .build();
        assertEquals(0, config.maxBodySize());
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = NegativeIntegerGenerator.class, count = 3)
    @SuppressWarnings("java:S5778")
    void shouldValidateNonNegativeBodySize(Integer negativeValue) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SecurityConfiguration.builder().maxBodySize(negativeValue).build());
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
                bodySize, false, false, true, false, false, false));
    }

    @Test
    void configurationAllowingNullBytesShouldNotBeLenient() {
        // Contract: lenient never permits null bytes
        SecurityConfiguration withNullBytes = SecurityConfiguration.builder()
                .allowDoubleEncoding(true)
                .allowNullBytes(true)
                .allowControlCharacters(true)
                .allowExtendedAscii(true)
                .normalizeUnicode(false)
                .failOnSuspiciousPatterns(false)
                .build();

        assertFalse(withNullBytes.isLenient());
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
