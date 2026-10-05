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
 * patterns using the curated null byte URL list and boundary patterns.
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
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("T4: Null Byte Path Traversal Attack Tests")
class NullBytePathTraversalAttackTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(SecurityConfiguration.defaults(), eventCounter);
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

    /**
     * The curated null byte URL list, complete: raw and percent-encoded null bytes, extension
     * bypasses and the combination with a traversal sequence. Every entry is rejected for its
     * null byte, which character validation finds before any other property of the path is
     * examined.
     *
     * @param nullByteUrl an entry of {@link NullByteURLGenerator#NULL_BYTE_URLS}
     */
    @ParameterizedTest
    @MethodSource("nullByteUrls")
    @DisplayName("Every curated null byte URL is rejected as NULL_BYTE_INJECTION")
    void shouldRejectEveryCuratedNullByteUrl(String nullByteUrl) {
        assertBoundaryPatternRejected(nullByteUrl, UrlSecurityFailureType.NULL_BYTE_INJECTION);
    }

    static Stream<String> nullByteUrls() {
        return NullByteURLGenerator.NULL_BYTE_URLS.stream();
    }

    private void assertBoundaryPatternRejected(String boundaryPattern, UrlSecurityFailureType expected) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(boundaryPattern),
                () -> "Boundary pattern should be rejected, length " + boundaryPattern.length());

        assertEquals(expected, exception.getFailureType(), "Unexpected verdict for the boundary pattern");
        assertEquals(boundaryPattern, exception.getOriginalInput(), "Original input should be preserved");
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }
}
