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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SecurityConfigurationBuilder}
 */
class SecurityConfigurationBuilderTest {

    @Test
    void shouldCreateBuilderWithDefaultPathSettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertEquals(4096, config.maxPathLength());
        assertFalse(config.allowDoubleEncoding());
    }

    @Test
    void shouldCreateBuilderWithDefaultParameterSettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertEquals(128, config.maxParameterNameLength());
        assertEquals(2048, config.maxParameterValueLength());
    }

    @Test
    void shouldCreateBuilderWithDefaultHeaderSettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertEquals(128, config.maxHeaderNameLength());
        assertEquals(2048, config.maxHeaderValueLength());
    }

    @Test
    void shouldCreateBuilderWithDefaultCookieSettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertEquals(128, config.maxCookieNameLength());
        assertEquals(2048, config.maxCookieValueLength());
    }

    @Test
    void shouldCreateBuilderWithDefaultBodySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertEquals(5L * 1024 * 1024, config.maxBodySize());
    }

    @Test
    void shouldCreateBuilderWithDefaultEncodingSettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertFalse(config.allowNullBytes());
        assertFalse(config.allowControlCharacters());
        assertTrue(config.allowExtendedAscii());
        assertTrue(config.normalizeUnicode()); // Enabled by default since 1.5 (NFKC)
    }

    @Test
    void shouldCreateBuilderWithDefaultPolicySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder().build();

        assertFalse(config.caseSensitiveComparison());
        assertFalse(config.failOnSuspiciousPatterns());
    }

    @Test
    void shouldSetPathSecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(1024)
                .allowDoubleEncoding(true)
                .build();

        assertEquals(1024, config.maxPathLength());
        assertTrue(config.allowDoubleEncoding());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidatePathLengthPositive() {
        var builder = SecurityConfiguration.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxPathLength(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxPathLength(-1));
    }

    @Test
    void shouldSetParameterSecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxParameterNameLength(64)
                .maxParameterValueLength(512)
                .build();

        assertEquals(64, config.maxParameterNameLength());
        assertEquals(512, config.maxParameterValueLength());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidateParameterConstraints() {
        var builder = SecurityConfiguration.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxParameterNameLength(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxParameterValueLength(0));
    }

    @Test
    void shouldSetHeaderSecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxHeaderNameLength(64)
                .maxHeaderValueLength(1024)
                .build();

        assertEquals(64, config.maxHeaderNameLength());
        assertEquals(1024, config.maxHeaderValueLength());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidateHeaderConstraints() {
        var builder = SecurityConfiguration.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxHeaderNameLength(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxHeaderValueLength(-5));
    }

    @Test
    void shouldSetCookieSecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxCookieNameLength(64)
                .maxCookieValueLength(512)
                .build();

        assertEquals(64, config.maxCookieNameLength());
        assertEquals(512, config.maxCookieValueLength());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidateCookieConstraints() {
        var builder = SecurityConfiguration.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxCookieNameLength(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxCookieValueLength(0));
    }

    @Test
    void shouldSetBodySecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxBodySize(1024 * 1024)
                .build();

        assertEquals(1024 * 1024, config.maxBodySize());
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldValidateBodySizeNonNegative() {
        var builder = SecurityConfiguration.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxBodySize(-1));
        // Zero is allowed (blocks all bodies)
        assertDoesNotThrow(() -> builder.maxBodySize(0));
    }

    @Test
    void shouldSetEncodingSecuritySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowNullBytes(true)
                .allowControlCharacters(true)
                .allowExtendedAscii(false)
                .normalizeUnicode(true)
                .build();

        assertTrue(config.allowNullBytes());
        assertTrue(config.allowControlCharacters());
        assertFalse(config.allowExtendedAscii());
        assertTrue(config.normalizeUnicode());
    }

    @Test
    void shouldSetEncodingSecurityInOneCall() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .encoding(true, true, false, true)
                .build();

        assertTrue(config.allowNullBytes());
        assertTrue(config.allowControlCharacters());
        assertFalse(config.allowExtendedAscii());
        assertTrue(config.normalizeUnicode());
    }

    @Test
    void shouldSetGeneralPolicySettings() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .caseSensitiveComparison(true)
                .failOnSuspiciousPatterns(true)
                .build();

        assertTrue(config.caseSensitiveComparison());
        assertTrue(config.failOnSuspiciousPatterns());
    }

    @Test
    void shouldSupportMethodChaining() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(2048)
                .allowDoubleEncoding(false)
                .maxParameterNameLength(64)
                .maxParameterValueLength(1024)
                .maxHeaderNameLength(64)
                .maxHeaderValueLength(2048)
                .maxCookieNameLength(64)
                .maxCookieValueLength(1024)
                .maxBodySize(2L * 1024 * 1024)
                .allowNullBytes(false)
                .allowControlCharacters(false)
                .allowExtendedAscii(false)
                .normalizeUnicode(true)
                .caseSensitiveComparison(true)
                .failOnSuspiciousPatterns(true)
                .build();

        assertEquals(2048, config.maxPathLength());
        assertEquals(64, config.maxParameterNameLength());
        assertEquals(1024, config.maxParameterValueLength());
        assertEquals(64, config.maxHeaderNameLength());
        assertEquals(2048, config.maxHeaderValueLength());
        assertEquals(64, config.maxCookieNameLength());
        assertEquals(1024, config.maxCookieValueLength());
        assertEquals(2L * 1024 * 1024, config.maxBodySize());
        assertFalse(config.allowExtendedAscii());
        assertTrue(config.normalizeUnicode());
        assertTrue(config.caseSensitiveComparison());
        assertTrue(config.failOnSuspiciousPatterns());
        assertTrue(config.isStrict());
    }

    @Test
    void builtConfigurationsShouldBeIndependent() {
        var builder = SecurityConfiguration.builder().maxPathLength(1000);
        SecurityConfiguration first = builder.build();
        builder.maxPathLength(2000);
        SecurityConfiguration second = builder.build();

        assertEquals(1000, first.maxPathLength());
        assertEquals(2000, second.maxPathLength());
    }
}
