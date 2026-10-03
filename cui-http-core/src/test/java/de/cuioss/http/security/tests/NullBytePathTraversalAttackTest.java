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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.url.NullByteURLGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T4: Test path traversal with null bytes
 *
 * <p>
 * This test class implements Task T4 from the HTTP security validation plan,
 * focusing on testing null byte injection attacks combined with path traversal
 * patterns using specialized generators and comprehensive attack vectors.
 * </p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>Raw null byte injection (\u0000)</li>
 *   <li>URL-encoded null bytes (%00)</li>
 *   <li>Null bytes combined with path traversal (../)</li>
 *   <li>Extension bypass attacks (file.jsp%00.png)</li>
 *   <li>Leading and trailing null byte patterns</li>
 *   <li>Multiple null byte sequences</li>
 *   <li>Null bytes in various URL components</li>
 *   <li>Boundary condition combinations</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>OWASP Top 10 - Path Traversal Prevention</li>
 *   <li>RFC 3986 URI Character Validation</li>
 *   <li>CVE-2004-0847, CVE-2005-0988, CVE-2006-1236 (Null byte attacks)</li>
 *   <li>File extension bypass protection</li>
 * </ul>
 *
 * Implements: Task T4 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T4: Null Byte Path Traversal Attack Tests")
class NullBytePathTraversalAttackTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;
    private SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Boundary family: null bytes. A raw or percent-encoded null byte is found by character
     * validation, which runs before pattern matching, so the verdict is the null byte even where
     * the input also carries a traversal sequence.
     *
     * @param boundaryPattern a boundary pattern carrying a null byte
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/file\0.txt",
            "/admin%00.php",
            "../etc/passwd%00.jpg",
            "file.jsp%00.png",
            "%00../../etc/shadow",
            "/%00../../../",
            "/\0/../\0/../file"
    })
    @DisplayName("Boundary patterns carrying a null byte are rejected as NULL_BYTE_INJECTION")
    void shouldRejectNullByteBoundaryPatterns(String boundaryPattern) {
        assertBoundaryPatternRejected(boundaryPattern, UrlSecurityFailureType.NULL_BYTE_INJECTION);
    }

    /**
     * Boundary family: C0 control characters in a path.
     *
     * @param boundaryPattern a boundary pattern carrying a raw control character
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/file\r\n.txt",
            "/path\t\tfile",
            "/dir\b\bfile",
            "/test\u001Ffile"
    })
    @DisplayName("Boundary patterns carrying a control character are rejected as CONTROL_CHARACTERS")
    void shouldRejectControlCharacterBoundaryPatterns(String boundaryPattern) {
        assertBoundaryPatternRejected(boundaryPattern, UrlSecurityFailureType.CONTROL_CHARACTERS);
    }

    /**
     * Boundary family: shell metacharacters outside the RFC 3986 path character set.
     *
     * @param boundaryPattern a boundary pattern carrying a pipe, backtick or redirection
     */
    @ParameterizedTest
    @ValueSource(strings = {"/file|command", "/file`command`", "/file>output"})
    @DisplayName("Boundary patterns carrying a non-path character are rejected as INVALID_CHARACTER")
    void shouldRejectNonPathCharacterBoundaryPatterns(String boundaryPattern) {
        assertBoundaryPatternRejected(boundaryPattern, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    /**
     * Boundary family: a deep run of traversal segments.
     */
    @Test
    @DisplayName("A deep traversal boundary pattern is rejected as PATH_TRAVERSAL_DETECTED")
    void shouldRejectDeepTraversalBoundaryPattern() {
        assertBoundaryPatternRejected("../".repeat(20) + "etc/passwd", UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED);
    }

    /**
     * Boundary family: a path longer than the default path limit.
     */
    @Test
    @DisplayName("An overlong boundary path is rejected as PATH_TOO_LONG")
    void shouldRejectOverlongBoundaryPath() {
        String overlongPath = "/" + "verylongpathsegment/".repeat(SecurityDefaults.MAX_PATH_LENGTH_DEFAULT / 20 + 1);

        assertBoundaryPatternRejected(overlongPath, UrlSecurityFailureType.PATH_TOO_LONG);
    }

    /**
     * Boundary family: nesting deeper than the path-depth limit.
     */
    @Test
    @DisplayName("An excessively nested boundary path is rejected as EXCESSIVE_NESTING")
    void shouldRejectExcessivelyNestedBoundaryPath() {
        assertBoundaryPatternRejected("dir/".repeat(200), UrlSecurityFailureType.EXCESSIVE_NESTING);
    }

    /**
     * Boundary family: patterns that probe a boundary without crossing it. A long path below
     * the path limit, nesting below the depth limit, and the path sub-delimiters {@code ;} and
     * {@code $} are legitimate and are returned unchanged.
     *
     * @param boundaryPattern a legitimate boundary pattern
     */
    @ParameterizedTest
    @MethodSource("legitimateBoundaryPatterns")
    @DisplayName("Boundary patterns within the limits are accepted unchanged")
    void shouldAcceptLegitimateBoundaryPatterns(String boundaryPattern) {
        Optional<String> validated = assertDoesNotThrow(() -> pipeline.validate(boundaryPattern),
                () -> "Legitimate boundary pattern should be accepted, length " + boundaryPattern.length());

        assertEquals(Optional.of(boundaryPattern), validated, "A legitimate path is returned unchanged");
        assertEquals(0, eventCounter.getTotalCount(), "An accepted path records no security event");
    }

    static Stream<String> legitimateBoundaryPatterns() {
        return Stream.of(
                "/" + "verylongpathsegment/".repeat(60),
                "dir/".repeat(50),
                "/file;command",
                "/file$variable");
    }

    private void assertBoundaryPatternRejected(String boundaryPattern, UrlSecurityFailureType expected) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(boundaryPattern),
                () -> "Boundary pattern should be rejected, length " + boundaryPattern.length());

        assertEquals(expected, exception.getFailureType(), "Unexpected verdict for the boundary pattern");
        assertEquals(boundaryPattern, exception.getOriginalInput(), "Original input should be preserved");
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }

    /**
     * Test focused null byte patterns using NullByteURLGenerator.
     *
     * <p>
     * This test specifically validates that null byte patterns are always rejected,
     * focusing on the core requirement of T4. Uses the NullByteURLGenerator which
     * produces comprehensive null byte patterns including raw nulls, encoded nulls,
     * extension bypasses, and path traversal combinations.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 25)
    @DisplayName("Focused null byte patterns must always be blocked")
    void shouldAlwaysBlockFocusedNullBytePatterns(String nullBytePattern) {
        // Given: A focused null byte pattern from NullByteURLGenerator
        long initialEventCount = eventCounter.getTotalCount();

        // When & Then: Null byte pattern must be rejected
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(nullBytePattern),
                "Null byte pattern must be rejected: " + nullBytePattern);

        // And: Exception should have proper details
        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType(),
                () -> "Unexpected verdict for pattern: " + nullBytePattern);

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for: " + nullBytePattern);
    }

    /**
     * Test dedicated null byte URL attack patterns.
     *
     * <p>
     * Uses NullByteURLGenerator which provides focused null byte injection
     * patterns specifically designed for URL context attacks.
     * </p>
     *
     * @param nullByteAttackPattern A null byte URL attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 50)
    @DisplayName("All null byte URL patterns should be rejected")
    void shouldRejectAllNullByteURLPatterns(String nullByteAttackPattern) {
        // Given: A null byte attack pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the null byte attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(nullByteAttackPattern),
                "Null byte attack pattern should be rejected: " + nullByteAttackPattern);

        // Then: The validation should fail with appropriate security event
        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType(),
                () -> "Unexpected verdict for pattern: " + nullByteAttackPattern);

        // And: Original malicious input should be preserved
        assertEquals(nullByteAttackPattern, exception.getOriginalInput(),
                "Original input should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded");
    }

    /**
     * Test specific high-risk null byte + path traversal combinations.
     *
     * <p>
     * This test ensures that the most dangerous null byte path traversal
     * combinations are consistently blocked.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 25)
    @DisplayName("High-risk null byte + path traversal combinations must be blocked")
    void shouldBlockHighRiskNullBytePathTraversalCombinations(String nullBytePathTraversalPattern) {
        // Given: A high-risk null byte + path traversal pattern from NullByteURLGenerator
        long initialEventCount = eventCounter.getTotalCount();

        // When & Then: High-risk null byte pattern should be rejected
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(nullBytePathTraversalPattern),
                "High-risk null byte pattern should be rejected: " + nullBytePathTraversalPattern);

        // And: Exception should be properly formed
        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType(),
                () -> "Unexpected verdict for pattern: " + nullBytePathTraversalPattern);

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount);
    }

    /**
     * Test null byte encoding variations.
     *
     * <p>
     * Tests different ways null bytes can be encoded in URLs to bypass
     * basic filtering mechanisms.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 20)
    @DisplayName("Null byte encoding variations must be blocked")
    void shouldBlockNullByteEncodingVariations(String nullByteEncodingPattern) {
        // Given: A null byte encoding variation from NullByteURLGenerator
        long initialEventCount = eventCounter.getTotalCount();

        // When & Then: Null byte encoding variation should be rejected
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(nullByteEncodingPattern),
                "Null byte encoding variation should be rejected: " + nullByteEncodingPattern);

        // And: The null byte is the verdict
        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount);
    }

    /**
     * Test file extension bypass attacks using null bytes.
     *
     * <p>
     * Validates protection against attacks that use null bytes to bypass
     * file extension filtering and access controls.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 22)
    @DisplayName("File extension bypass attacks using null bytes must be blocked")
    void shouldBlockExtensionBypassAttacks(String extensionBypassPattern) {
        // Given: A file extension bypass pattern with null bytes from NullByteURLGenerator
        long initialEventCount = eventCounter.getTotalCount();

        // When & Then: Extension bypass attack should be rejected
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(extensionBypassPattern),
                "Extension bypass attack should be rejected: " + extensionBypassPattern);

        // And: The null byte is the verdict
        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount);
    }
}