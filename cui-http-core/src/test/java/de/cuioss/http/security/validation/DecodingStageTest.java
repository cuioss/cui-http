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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link DecodingStage}
 */
class DecodingStageTest {

    private SecurityConfiguration defaultConfig;
    private SecurityConfiguration strictConfig;
    private DecodingStage pathDecoder;
    private DecodingStage parameterDecoder;

    @BeforeEach
    void setUp() {
        defaultConfig = SecurityConfiguration.defaults();
        strictConfig = SecurityConfiguration.strict();

        pathDecoder = new DecodingStage(defaultConfig, ValidationType.URL_PATH);
        parameterDecoder = new DecodingStage(defaultConfig, ValidationType.PARAMETER_VALUE);
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInput() {
        assertEquals(Optional.empty(), pathDecoder.validate(null));
        assertEquals(Optional.empty(), parameterDecoder.validate(null));
    }

    @Test
    @DisplayName("Should handle empty input")
    void shouldHandleEmptyInput() {
        Optional<String> result1 = pathDecoder.validate("");
        assertTrue(result1.isPresent());
        assertEquals("", result1.get());
        Optional<String> result2 = parameterDecoder.validate("");
        assertTrue(result2.isPresent());
        assertEquals("", result2.get());
    }

    @Test
    @DisplayName("Should decode standard URL encoding")
    void shouldDecodeStandardUrlEncoding() {
        // Basic percent encoding
        Optional<String> result1 = pathDecoder.validate("/api/users");
        assertTrue(result1.isPresent());
        assertEquals("/api/users", result1.get());
        Optional<String> result2 = pathDecoder.validate("/api/users%2F123");
        assertTrue(result2.isPresent());
        assertEquals("/api/users/123", result2.get());
        Optional<String> result3 = parameterDecoder.validate("hello%20world");
        assertTrue(result3.isPresent());
        assertEquals("hello world", result3.get());
        Optional<String> result4 = parameterDecoder.validate("user%40example.com");
        assertTrue(result4.isPresent());
        assertEquals("user@example.com", result4.get());

        // Special characters
        Optional<String> result5 = pathDecoder.validate("path%20with%20spaces");
        assertTrue(result5.isPresent());
        assertEquals("path with spaces", result5.get());
        Optional<String> result6 = parameterDecoder.validate("query%3Dvalue%26other%3Ddata");
        assertTrue(result6.isPresent());
        assertEquals("query=value&other=data", result6.get());
        Optional<String> result7 = pathDecoder.validate("file.txt");
        assertTrue(result7.isPresent());
        assertEquals("file.txt", result7.get()); // No encoding needed
    }

    @Test
    @DisplayName("Should detect and block double encoding when not allowed")
    void shouldDetectDoubleEncoding() {
        // Default config doesn't allow double encoding
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate("/admin%252Fusers")); // %25 = encoded %

        assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertEquals("/admin%252Fusers", exception.getOriginalInput());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Double encoding pattern"));
    }

    @ParameterizedTest
    @DisplayName("Should detect various double encoding patterns")
    @ValueSource(strings = {
            "%252F", // %2F encoded again
            "%2525", // %25 encoded again
            "/path%252E%252E/admin", // ../ double encoded
            "%252E%252E%252F", // ../ fully double encoded
            "file%252Etxt", // file.txt with double encoded dot
            "%2520", // space double encoded
    })
    void shouldDetectVariousDoubleEncodingPatterns(String input) {
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate(input));

        assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, exception.getFailureType());
        assertEquals(input, exception.getOriginalInput());
    }

    @Test
    @DisplayName("Should allow double encoding when configured")
    void shouldAllowDoubleEncodingWhenConfigured() {
        SecurityConfiguration allowingConfig = SecurityConfiguration.builder()
                .allowDoubleEncoding(true)
                .normalizeUnicode(false)
                .build();

        DecodingStage lenientDecoder = new DecodingStage(allowingConfig, ValidationType.URL_PATH);

        // This should not throw an exception
        Optional<String> result = lenientDecoder.validate("/admin%252Fusers");
        assertTrue(result.isPresent());
        assertEquals("/admin%2Fusers", result.get()); // First layer decoded
    }

    /**
     * QI-14/QI-10: Converted hardcoded String[] to dynamic parameterized test.
     * Tests invalid URL encoding patterns that should trigger validation failures.
     */
    @ParameterizedTest
    @MethodSource("invalidEncodingPatterns")
    @DisplayName("Should detect invalid encoding sequences")
    void shouldDetectInvalidEncoding(String invalidInput) {
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate(invalidInput),
                "Should detect invalid encoding in: " + invalidInput);

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertEquals(invalidInput, exception.getOriginalInput());
        assertNotNull(exception.getCause());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("URL decoding failed"));
    }

    /**
     * QI-14/QI-10: Dynamic data source for invalid encoding patterns.
     * Replaces hardcoded array with method-based generation for better maintainability.
     */
    private static Stream<String> invalidEncodingPatterns() {
        return Stream.of(
                "%Z1",    // Invalid hex character (Z)
                "%1",     // Incomplete encoding (single digit)
                "%",      // Incomplete encoding (no digits)
                "%2G",    // Invalid hex character (G)
                "%%20",   // Double percent without proper hex
                "%XY",    // Invalid hex characters (X, Y)
                "%0",     // Incomplete encoding
                "%GH",    // Invalid hex characters
                "%2Z",    // Invalid second hex character
                "%W1"     // Invalid first hex character
        );
    }

    @Test
    @DisplayName("Should handle Unicode normalization when enabled")
    void shouldHandleUnicodeNormalization() {
        SecurityConfiguration unicodeConfig = SecurityConfiguration.builder()
                .normalizeUnicode(true)
                .build();

        DecodingStage unicodeDecoder = new DecodingStage(unicodeConfig, ValidationType.URL_PATH);

        // String that doesn't change with normalization
        String normalInput = "regular-path";
        Optional<String> result1 = unicodeDecoder.validate(normalInput);
        assertTrue(result1.isPresent());
        assertEquals(normalInput, result1.get());

        // Test with already normalized Unicode
        String normalizedUnicode = "café"; // NFC form
        Optional<String> result2 = unicodeDecoder.validate(normalizedUnicode);
        assertTrue(result2.isPresent());
        assertEquals(normalizedUnicode, result2.get());
    }

    @Test
    @DisplayName("Should detect Unicode normalization changes")
    void shouldDetectUnicodeNormalizationChanges() {
        SecurityConfiguration unicodeConfig = SecurityConfiguration.builder()
                .normalizeUnicode(true)
                .build();

        DecodingStage unicodeDecoder = new DecodingStage(unicodeConfig, ValidationType.URL_PATH);

        // Create a string with decomposed Unicode that will change when normalized
        // Using combining characters that will be normalized
        String decomposed = "cafe\u0301"; // e + combining acute accent
        String composed = "café"; // precomposed character

        // If the input changes during normalization, it should throw an exception
        // Note: This depends on the exact Unicode composition
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> unicodeDecoder.validate(decomposed));

        assertEquals(UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED, exception.getFailureType());
        assertEquals(decomposed, exception.getOriginalInput());
        assertEquals(Optional.of(composed), exception.getSanitizedInput());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Unicode normalization changed"));
    }

    @Test
    @DisplayName("Should skip Unicode normalization when disabled")
    void shouldSkipUnicodeNormalizationWhenDisabled() {
        SecurityConfiguration noUnicodeConfig = SecurityConfiguration.builder()
                .normalizeUnicode(false)
                .build();

        DecodingStage noUnicodeDecoder = new DecodingStage(noUnicodeConfig, ValidationType.URL_PATH);

        // Even with decomposed Unicode, should not throw exception
        String decomposed = "cafe\u0301";
        assertDoesNotThrow(() -> {
            Optional<String> result = noUnicodeDecoder.validate(decomposed);
            assertTrue(result.isPresent());
            return result.get();
        });
    }

    @Test
    @DisplayName("Should preserve validation type in exceptions")
    void shouldPreserveValidationType() {
        DecodingStage[] decoders = {
                new DecodingStage(defaultConfig, ValidationType.URL_PATH),
                new DecodingStage(defaultConfig, ValidationType.PARAMETER_NAME),
                new DecodingStage(defaultConfig, ValidationType.PARAMETER_VALUE),
                new DecodingStage(defaultConfig, ValidationType.HEADER_VALUE)
        };

        ValidationType[] expectedTypes = {
                ValidationType.URL_PATH,
                ValidationType.PARAMETER_NAME,
                ValidationType.PARAMETER_VALUE,
                ValidationType.HEADER_VALUE
        };

        for (int i = 0; i < decoders.length; i++) {
            final int index = i; // Make effectively final for lambda
            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> decoders[index].validate("%252F"));

            assertEquals(expectedTypes[i], exception.getValidationType());
        }
    }

    @Test
    @DisplayName("Should be immutable and thread-safe")
    @SuppressWarnings("java:S1612")
    void shouldBeImmutableAndThreadSafe() {
        // Verify immutability via Lombok @Value annotation (check class methods are present)
        // Lombok @Value generates equals, hashCode, toString, and makes fields final
        assertDoesNotThrow(() -> DecodingStage.class.getMethod("equals", Object.class));
        assertDoesNotThrow(() -> DecodingStage.class.getMethod("hashCode"));
        assertDoesNotThrow(() -> DecodingStage.class.getMethod("toString"));

        // Test concurrent access
        DecodingStage decoder = new DecodingStage(defaultConfig, ValidationType.URL_PATH);

        // Run multiple threads concurrently
        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];

        for (int i = 0; i < 10; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    String input = "/api/path%2F" + threadIndex;
                    Optional<String> optionalResult = decoder.validate(input);
                    assertTrue(optionalResult.isPresent());
                    String result = optionalResult.get();
                    results[threadIndex] = result.equals("/api/path/" + threadIndex);
                } catch (UrlSecurityException e) {
                    results[threadIndex] = false;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads
        for (Thread thread : threads) {

            assertDoesNotThrow(() -> thread.join());
        }

        // Verify all succeeded
        for (boolean result : results) {
            assertTrue(result);
        }
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void shouldProvideMeaningfulToString() {
        String pathString = pathDecoder.toString();
        assertTrue(pathString.contains("DecodingStage"));
        assertTrue(pathString.contains("URL_PATH"));
        assertTrue(pathString.contains("allowDoubleEncoding"));
        assertTrue(pathString.contains("normalizeUnicode"));

        // Test with different config
        DecodingStage strictDecoder = new DecodingStage(strictConfig, ValidationType.PARAMETER_NAME);
        String strictString = strictDecoder.toString();
        assertTrue(strictString.contains("PARAMETER_NAME"));
    }

    @Test
    @DisplayName("Should support conditional validation")
    void shouldSupportConditionalValidation() {
        // Create conditional validator that skips null/empty
        var conditionalDecoder = pathDecoder.when(input -> input != null && !input.isEmpty());

        // Should pass through null and empty without validation
        assertEquals(Optional.empty(), conditionalDecoder.validate(null));
        Optional<String> result1 = conditionalDecoder.validate("");
        assertTrue(result1.isPresent());
        assertEquals("", result1.get());

        // Should validate non-empty input
        Optional<String> result2 = conditionalDecoder.validate("/api%2Fpath");
        assertTrue(result2.isPresent());
        assertEquals("/api/path", result2.get());

        // Should still throw on double encoding for non-empty input
        assertThrows(UrlSecurityException.class,
                () -> conditionalDecoder.validate("%252F"));
    }

    /**
     * Test data provider for complex decoding scenarios
     */
    static Stream<Arguments> complexDecodingScenarios() {
        return Stream.of(
                Arguments.of("/api/users%2F123", "/api/users/123", "Basic path separator"),
                Arguments.of("name%3DJohn%26age%3D30", "name=John&age=30", "Query parameter format"),
                Arguments.of("hello%2Bworld", "hello+world", "Plus sign encoding"),
                Arguments.of("50%25%20off", "50% off", "Percent sign in text"),
                Arguments.of("file%2Etxt", "file.txt", "Dot encoding"),
                Arguments.of("path%2F..%2Fadmin", "path/../admin", "Path traversal pattern"),
                Arguments.of("%C2%A3100", "£100", "Unicode currency symbol"),
                Arguments.of("data%5B0%5D", "data[0]", "Array notation")
        );
    }

    @ParameterizedTest
    @DisplayName("Should handle complex decoding scenarios")
    @MethodSource("complexDecodingScenarios")
    void shouldHandleComplexDecodingScenarios(String input, String expected, String description) {
        Optional<String> result = pathDecoder.validate(input);
        assertTrue(result.isPresent(), description);
        assertEquals(expected, result.get(), description);
    }

    @Test
    @DisplayName("Should handle configuration edge cases")
    void shouldHandleConfigurationEdgeCases() {
        // Test with minimal configuration
        SecurityConfiguration minimalConfig = SecurityConfiguration.builder()
                .allowDoubleEncoding(false)
                .normalizeUnicode(false)
                .build();

        DecodingStage minimalDecoder = new DecodingStage(minimalConfig, ValidationType.URL_PATH);

        Optional<String> result = minimalDecoder.validate("/api%2Fpath");
        assertTrue(result.isPresent());
        assertEquals("/api/path", result.get());

        // Should still detect double encoding
        assertThrows(UrlSecurityException.class,
                () -> minimalDecoder.validate("%252F"));
    }

    // QI-2: New tests for enhanced multi-layer decoding capabilities

    @Test
    @DisplayName("Should NOT decode HTML entities - application layer responsibility")
    @SuppressWarnings("java:S5961")
    void shouldNotDecodeHtmlEntities() {
        // HTML entities should pass through unchanged - they are application-layer encodings
        assertEquals("&lt;/script&gt;", pathDecoder.validate("&lt;/script&gt;").orElse(null));
        assertEquals("path&sol;..&sol;admin", pathDecoder.validate("path&sol;..&sol;admin").orElse(null));
        assertEquals("alert(&apos;xss&apos;)", pathDecoder.validate("alert(&apos;xss&apos;)").orElse(null));
        assertEquals("data=&quot;value&quot;", pathDecoder.validate("data=&quot;value&quot;").orElse(null));
        assertEquals("user &amp; admin", pathDecoder.validate("user &amp; admin").orElse(null));

        // Decimal numeric entities should pass through unchanged
        assertEquals("&#47;admin", pathDecoder.validate("&#47;admin").orElse(null));
        assertEquals("&#60;script&#62;", pathDecoder.validate("&#60;script&#62;").orElse(null));
        assertEquals("&#39;attack&#39;", pathDecoder.validate("&#39;attack&#39;").orElse(null));
        assertEquals("&#34;injection&#34;", pathDecoder.validate("&#34;injection&#34;").orElse(null));

        // Hexadecimal numeric entities should pass through unchanged
        assertEquals("&#x2F;", pathDecoder.validate("&#x2F;").orElse(null));
        assertEquals("&#x3C;", pathDecoder.validate("&#x3C;").orElse(null));
        assertEquals("&#x3E;", pathDecoder.validate("&#x3E;").orElse(null));
        assertEquals("&#x27;", pathDecoder.validate("&#x27;").orElse(null));
        assertEquals("&#x22;", pathDecoder.validate("&#x22;").orElse(null));
    }

    @Test
    @DisplayName("Should NOT decode JavaScript escapes - application layer responsibility")
    @SuppressWarnings("java:S5961")
    void shouldNotDecodeJavaScriptEscapes() {
        // JavaScript escapes should pass through unchanged - they are application-layer encodings
        assertEquals("\\x3c/script\\x3e", pathDecoder.validate("\\x3c/script\\x3e").orElse(null));
        assertEquals("\\x2fadmin", pathDecoder.validate("\\x2fadmin").orElse(null));
        assertEquals("\\x27attack\\x27", pathDecoder.validate("\\x27attack\\x27").orElse(null));
        assertEquals("\\x22injection\\x22", pathDecoder.validate("\\x22injection\\x22").orElse(null));

        // Unicode escapes should pass through unchanged
        assertEquals("\\u003cscript\\u003e", pathDecoder.validate("\\u003cscript\\u003e").orElse(null));
        assertEquals("\\u002fpath", pathDecoder.validate("\\u002fpath").orElse(null));
        assertEquals("\\u0027xss\\u0027", pathDecoder.validate("\\u0027xss\\u0027").orElse(null));
        assertEquals("\\u0022sqli\\u0022", pathDecoder.validate("\\u0022sqli\\u0022").orElse(null));

        // Octal escapes should pass through unchanged
        assertEquals("\\057", pathDecoder.validate("\\057").orElse(null));
        assertEquals("\\074", pathDecoder.validate("\\074").orElse(null));
        assertEquals("\\076", pathDecoder.validate("\\076").orElse(null));
        assertEquals("\\047", pathDecoder.validate("\\047").orElse(null));
        assertEquals("\\042", pathDecoder.validate("\\042").orElse(null));
    }

    @Test
    @DisplayName("Should handle HTTP protocol encoding only - no cross-layer mixing")
    void shouldHandleHttpProtocolEncodingOnly() {
        // URL encoding should be decoded (HTTP protocol layer)
        assertEquals("&lt;/script&gt;", pathDecoder.validate("&lt;%2Fscript&gt;").orElse(null));
        assertEquals("path&sol;../admin", pathDecoder.validate("path&sol;..%2Fadmin").orElse(null));

        // JavaScript escapes should pass through, URL encoding should be decoded
        assertEquals("\\x3c/script\\x3e", pathDecoder.validate("\\x3c%2Fscript\\x3e").orElse(null));
        assertEquals("\\x2Fadmin", pathDecoder.validate("\\x2F%61dmin").orElse(null)); // %61 = 'a'

        // HTML entities and JavaScript escapes should both pass through unchanged
        assertEquals("&lt;script&gt;alert(\\x27xss\\x27)&lt;/script&gt;", pathDecoder.validate("&lt;script&gt;alert(\\x27xss\\x27)&lt;/script&gt;").orElse(null));

        // Only URL encoding should be decoded from mixed input
        assertEquals("path&sol;..\\x2Fadmin", pathDecoder.validate("path&sol;..\\x2F%61dmin").orElse(null));
    }

    @Test
    @DisplayName("Should pass through all application-layer encodings unchanged")
    void shouldPassThroughApplicationLayerEncodingsUnchanged() {
        // HTML entities should remain unchanged (application layer responsibility)
        assertEquals("&invalid;", pathDecoder.validate("&invalid;").orElse(null));
        assertEquals("&#9999999;", pathDecoder.validate("&#9999999;").orElse(null)); // Invalid codepoint (too large)
        assertEquals("&#xZZZZ;", pathDecoder.validate("&#xZZZZ;").orElse(null)); // Invalid hex

        // JavaScript escapes should remain unchanged (application layer responsibility)
        assertEquals("\\xZZ", pathDecoder.validate("\\xZZ").orElse(null)); // Invalid hex
        assertEquals("\\uZZZZ", pathDecoder.validate("\\uZZZZ").orElse(null)); // Invalid unicode
        assertEquals("\\999", pathDecoder.validate("\\999").orElse(null)); // Invalid octal (> 255)

        // Mixed with valid URL encoding (only URL encoding should be processed)
        assertEquals("valid&sol;&invalid;", pathDecoder.validate("valid&sol;&invalid;").orElse(null));
    }

    @Test
    @DisplayName("Should process only HTTP protocol-layer decoding")
    void shouldProcessOnlyHttpProtocolLayerDecoding() {
        // HTML entities should NOT be decoded - application layer responsibility
        String htmlEntities = "&lt;script&gt;"; // Should remain unchanged
        assertEquals("&lt;script&gt;", pathDecoder.validate(htmlEntities).orElse(null));

        // JavaScript escapes should NOT be decoded - application layer responsibility
        String jsEscapes = "\\x3cscript\\x3e"; // Should remain unchanged
        assertEquals("\\x3cscript\\x3e", pathDecoder.validate(jsEscapes).orElse(null));

        // URL encoding SHOULD be decoded - HTTP protocol layer responsibility
        String urlEncoding = "%3Cscript%3E"; // Should decode to <script>
        assertEquals("<script>", pathDecoder.validate(urlEncoding).orElse(null));

        // UTF-8 overlong encoding should be detected and blocked
        assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate("%c0%ae")); // UTF-8 overlong for '.'
    }

    @Test
    @DisplayName("Should maintain HTTP protocol-layer functionality")
    void shouldMaintainHttpProtocolLayerFunctionality() {
        // URL decoding should continue to work (HTTP protocol layer)
        Optional<String> result1 = pathDecoder.validate("/api/users%2F123");
        assertTrue(result1.isPresent());
        assertEquals("/api/users/123", result1.get());
        Optional<String> result2 = parameterDecoder.validate("hello%20world");
        assertTrue(result2.isPresent());
        assertEquals("hello world", result2.get());
        Optional<String> result3 = parameterDecoder.validate("user%40example.com");
        assertTrue(result3.isPresent());
        assertEquals("user@example.com", result3.get());

        // Double encoding detection should still work (HTTP protocol layer)
        assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate("/admin%252Fusers"));

        // Unicode normalization should still work when enabled (HTTP protocol layer)
        SecurityConfiguration unicodeConfig = SecurityConfiguration.builder()
                .normalizeUnicode(true)
                .build();
        DecodingStage unicodeDecoder = new DecodingStage(unicodeConfig, ValidationType.URL_PATH);

        String normalInput = "regular-path";
        Optional<String> result = unicodeDecoder.validate(normalInput);
        assertTrue(result.isPresent());
        assertEquals(normalInput, result.get());

        // UTF-8 overlong encoding detection should work (HTTP protocol layer)
        assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate("%c0%af")); // UTF-8 overlong for '/'
    }

    /**
     * Test data for HTTP protocol-layer encoding scenarios only
     */
    static Stream<Arguments> httpProtocolEncodingScenarios() {
        return Stream.of(
                // URL encoding scenarios (HTTP protocol layer - should be decoded)
                Arguments.of("%3Cscript%3E", "<script>", "URL encoded script tags"),
                Arguments.of("%2F..%2Fadmin", "/../admin", "URL encoded path traversal"),
                Arguments.of("cmd%26%26echo%20test", "cmd&&echo test", "URL encoded command injection"),
                Arguments.of("%27%20OR%201%3D1%20--", "' OR 1=1 --", "URL encoded SQL injection"),

                // Application-layer encodings (should pass through unchanged)
                Arguments.of("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;script&gt;alert(1)&lt;/script&gt;", "HTML entities pass through"),
                Arguments.of("\\x3cimg src=x\\x3e", "\\x3cimg src=x\\x3e", "JS hex escapes pass through"),
                Arguments.of("\\u003cscript\\u003e", "\\u003cscript\\u003e", "JS Unicode escapes pass through"),

                // Mixed scenarios - only URL encoding should be processed
                Arguments.of("&lt;script%3E", "&lt;script>", "HTML entity + URL encoding"),
                Arguments.of("\\x3cscript%3E", "\\x3cscript>", "JS escape + URL encoding"),
                Arguments.of("&lt;%2Fscript&gt;", "&lt;/script&gt;", "HTML entities with URL encoded slash")
        );
    }

    @ParameterizedTest
    @DisplayName("Should handle HTTP protocol-layer encoding scenarios correctly")
    @MethodSource("httpProtocolEncodingScenarios")
    void shouldHandleHttpProtocolEncodingScenarios(String input, String expected, String description) {
        // Specifically test that only HTTP protocol-layer encoding is processed
        // while application-layer encodings (HTML entities, JS escapes) pass through unchanged
        Optional<String> result = pathDecoder.validate(input);
        assertTrue(result.isPresent());
        String resultString = result.get();
        assertEquals(expected, resultString, description);

        // Additional validation: ensure the result contains expected patterns for application-layer encodings
        if (description.contains("pass through")) {
            // For pass-through scenarios, input and result should be identical (no decoding occurred)
            assertEquals(input, resultString, "Application-layer encoding should pass through unchanged");
        } else if (description.contains("URL encoded")) {
            // For URL encoding scenarios, ensure actual decoding occurred
            assertNotEquals(input, resultString, "URL encoding should be decoded at HTTP protocol layer");
        }
    }

    @ParameterizedTest
    @DisplayName("Should detect UTF-8 overlong encoding attacks")
    @ValueSource(strings = {
            "%c0%ae",          // Overlong encoding for '.'
            "%c0%af",          // Overlong encoding for '/'
            "%c1%9c",          // Overlong encoding
            "%c0%a0",          // Overlong encoding for space
            "%e0%80%af",       // 3-byte overlong for '/'
            "%f0%80%80%af"     // 4-byte overlong for '/'
    })
    void shouldDetectUtf8OverlongEncodingAttacks(String overlongInput) {
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pathDecoder.validate(overlongInput),
                "Should detect UTF-8 overlong encoding in: " + overlongInput);

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("UTF-8 overlong encoding attack"));
    }

    // Architectural decision: Application-layer encodings (HTML entities, JS escapes, Base64)
    // are handled by higher application layers where they have proper context.
}