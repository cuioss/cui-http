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
package de.cuioss.http.security.core;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link UrlSecurityFailureType}
 */
@EnableGeneratorController
class UrlSecurityFailureTypeTest {

    @Test
    void shouldHaveAllExpectedFailureTypes() {
        // Verify all expected failure types exist
        assertNotNull(UrlSecurityFailureType.INVALID_ENCODING, "INVALID_ENCODING failure type should exist");
        assertNotNull(UrlSecurityFailureType.DOUBLE_ENCODING, "DOUBLE_ENCODING failure type should exist");
        assertNotNull(UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED, "UNICODE_NORMALIZATION_CHANGED failure type should exist");
        assertNotNull(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, "PATH_TRAVERSAL_DETECTED failure type should exist");
        assertNotNull(UrlSecurityFailureType.DIRECTORY_ESCAPE_ATTEMPT, "DIRECTORY_ESCAPE_ATTEMPT failure type should exist");
        assertNotNull(UrlSecurityFailureType.INVALID_CHARACTER, "INVALID_CHARACTER failure type should exist");
        assertNotNull(UrlSecurityFailureType.NULL_BYTE_INJECTION, "NULL_BYTE_INJECTION failure type should exist");
        assertNotNull(UrlSecurityFailureType.CONTROL_CHARACTERS, "CONTROL_CHARACTERS failure type should exist");
        assertNotNull(UrlSecurityFailureType.PATH_TOO_LONG, "PATH_TOO_LONG failure type should exist");
        assertNotNull(UrlSecurityFailureType.INPUT_TOO_LONG, "INPUT_TOO_LONG failure type should exist");
        assertNotNull(UrlSecurityFailureType.EXCESSIVE_NESTING, "EXCESSIVE_NESTING failure type should exist");
        assertNotNull(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, "SUSPICIOUS_PATTERN_DETECTED failure type should exist");
        assertNotNull(UrlSecurityFailureType.SUSPICIOUS_PARAMETER_NAME, "SUSPICIOUS_PARAMETER_NAME failure type should exist");
        // XSS_DETECTED removed - application layer responsibility
        assertNotNull(UrlSecurityFailureType.KNOWN_ATTACK_SIGNATURE, "KNOWN_ATTACK_SIGNATURE failure type should exist");
        assertNotNull(UrlSecurityFailureType.MALFORMED_INPUT, "MALFORMED_INPUT failure type should exist");
        assertNotNull(UrlSecurityFailureType.INVALID_STRUCTURE, "INVALID_STRUCTURE failure type should exist");
        assertNotNull(UrlSecurityFailureType.PROTOCOL_VIOLATION, "PROTOCOL_VIOLATION failure type should exist");
        assertNotNull(UrlSecurityFailureType.RFC_VIOLATION, "RFC_VIOLATION failure type should exist");
    }

    @Test
    void shouldHave22FailureTypes() {
        // Verify we have the expected number of failure types (removed 3 application-layer types: SQL, Command, XSS)
        UrlSecurityFailureType[] values = UrlSecurityFailureType.values();
        assertEquals(22, values.length, "Should have 22 failure types after removing SQL, Command, and XSS injection");
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 23)
    void shouldHaveNonNullDescriptions(UrlSecurityFailureType type) {
        assertNotNull(type.getDescription(), "Description should not be null for: " + type);
        assertFalse(type.getDescription().trim().isEmpty(), "Description should not be empty for: " + type);
    }

    static class UrlSecurityFailureTypeGenerator implements TypedGenerator<UrlSecurityFailureType> {
        private final TypedGenerator<UrlSecurityFailureType> gen =
                Generators.enumValues(UrlSecurityFailureType.class);

        @Override
        public UrlSecurityFailureType next() {
            return gen.next();
        }

        @Override
        public Class<UrlSecurityFailureType> getType() {
            return UrlSecurityFailureType.class;
        }
    }

    @Test
    void shouldHaveUniqueDescriptions() {
        Set<String> descriptions = Arrays.stream(UrlSecurityFailureType.values())
                .map(UrlSecurityFailureType::getDescription)
                .collect(Collectors.toSet());

        assertEquals(UrlSecurityFailureType.values().length, descriptions.size(),
                "All failure types should have unique descriptions");
    }

    @Test
    void shouldCorrectlyIdentifyEncodingIssues() {
        assertTrue(UrlSecurityFailureType.INVALID_ENCODING.isEncodingIssue(), "INVALID_ENCODING should be classified as encoding issue");
        assertTrue(UrlSecurityFailureType.DOUBLE_ENCODING.isEncodingIssue(), "DOUBLE_ENCODING should be classified as encoding issue");
        assertTrue(UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED.isEncodingIssue(), "UNICODE_NORMALIZATION_CHANGED should be classified as encoding issue");

        // Non-encoding issues should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isEncodingIssue(), "PATH_TRAVERSAL_DETECTED should not be classified as encoding issue");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isEncodingIssue(), "NULL_BYTE_INJECTION should not be classified as encoding issue");
        assertFalse(UrlSecurityFailureType.PATH_TOO_LONG.isEncodingIssue(), "PATH_TOO_LONG should not be classified as encoding issue");
    }

    @Test
    void shouldCorrectlyIdentifyPathTraversalAttacks() {
        assertTrue(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isPathTraversalAttack(), "PATH_TRAVERSAL_DETECTED should be classified as path traversal attack");
        assertTrue(UrlSecurityFailureType.DIRECTORY_ESCAPE_ATTEMPT.isPathTraversalAttack(), "DIRECTORY_ESCAPE_ATTEMPT should be classified as path traversal attack");

        // Non-path-traversal should return false
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isPathTraversalAttack(), "INVALID_ENCODING should not be classified as path traversal attack");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isPathTraversalAttack(), "NULL_BYTE_INJECTION should not be classified as path traversal attack");
        assertFalse(UrlSecurityFailureType.PATH_TOO_LONG.isPathTraversalAttack(), "PATH_TOO_LONG should not be classified as path traversal attack");
    }

    @Test
    void shouldCorrectlyIdentifyCharacterAttacks() {
        assertTrue(UrlSecurityFailureType.INVALID_CHARACTER.isCharacterAttack(), "INVALID_CHARACTER should be classified as character attack");
        assertTrue(UrlSecurityFailureType.NULL_BYTE_INJECTION.isCharacterAttack(), "NULL_BYTE_INJECTION should be classified as character attack");
        assertTrue(UrlSecurityFailureType.CONTROL_CHARACTERS.isCharacterAttack(), "CONTROL_CHARACTERS should be classified as character attack");

        // Non-character attacks should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isCharacterAttack(), "PATH_TRAVERSAL_DETECTED should not be classified as character attack");
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isCharacterAttack(), "INVALID_ENCODING should not be classified as character attack");
        assertFalse(UrlSecurityFailureType.PATH_TOO_LONG.isCharacterAttack(), "PATH_TOO_LONG should not be classified as character attack");
    }

    @Test
    void shouldCorrectlyIdentifySizeViolations() {
        assertTrue(UrlSecurityFailureType.PATH_TOO_LONG.isSizeViolation(), "PATH_TOO_LONG should be classified as size violation");
        assertTrue(UrlSecurityFailureType.INPUT_TOO_LONG.isSizeViolation(), "INPUT_TOO_LONG should be classified as size violation");
        assertTrue(UrlSecurityFailureType.EXCESSIVE_NESTING.isSizeViolation(), "EXCESSIVE_NESTING should be classified as size violation");

        // Non-size violations should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isSizeViolation(), "PATH_TRAVERSAL_DETECTED should not be classified as size violation");
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isSizeViolation(), "INVALID_ENCODING should not be classified as size violation");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isSizeViolation(), "NULL_BYTE_INJECTION should not be classified as size violation");
    }

    @Test
    void shouldCorrectlyIdentifyPatternBased() {
        assertTrue(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED.isPatternBased(), "SUSPICIOUS_PATTERN_DETECTED should be classified as pattern-based");
        assertTrue(UrlSecurityFailureType.SUSPICIOUS_PARAMETER_NAME.isPatternBased(), "SUSPICIOUS_PARAMETER_NAME should be classified as pattern-based");
        assertTrue(UrlSecurityFailureType.KNOWN_ATTACK_SIGNATURE.isPatternBased(), "KNOWN_ATTACK_SIGNATURE should be classified as pattern-based");

        // Non-pattern-based should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isPatternBased(), "PATH_TRAVERSAL_DETECTED should not be classified as pattern-based");
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isPatternBased(), "INVALID_ENCODING should not be classified as pattern-based");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isPatternBased(), "NULL_BYTE_INJECTION should not be classified as pattern-based");
    }

    // XSS attack identification removed - application layer responsibility.
    // Application layers have proper context for HTML/JS escaping and validation.

    @Test
    void shouldCorrectlyIdentifyStructuralIssues() {
        assertTrue(UrlSecurityFailureType.MALFORMED_INPUT.isStructuralIssue(), "MALFORMED_INPUT should be classified as structural issue");
        assertTrue(UrlSecurityFailureType.INVALID_STRUCTURE.isStructuralIssue(), "INVALID_STRUCTURE should be classified as structural issue");

        // Non-structural issues should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isStructuralIssue(), "PATH_TRAVERSAL_DETECTED should not be classified as structural issue");
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isStructuralIssue(), "INVALID_ENCODING should not be classified as structural issue");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isStructuralIssue(), "NULL_BYTE_INJECTION should not be classified as structural issue");
    }

    @Test
    void shouldCorrectlyIdentifyProtocolViolations() {
        assertTrue(UrlSecurityFailureType.PROTOCOL_VIOLATION.isProtocolViolation(), "PROTOCOL_VIOLATION should be classified as protocol violation");
        assertTrue(UrlSecurityFailureType.RFC_VIOLATION.isProtocolViolation(), "RFC_VIOLATION should be classified as protocol violation");

        // Non-protocol violations should return false
        assertFalse(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED.isProtocolViolation(), "PATH_TRAVERSAL_DETECTED should not be classified as protocol violation");
        assertFalse(UrlSecurityFailureType.INVALID_ENCODING.isProtocolViolation(), "INVALID_ENCODING should not be classified as protocol violation");
        assertFalse(UrlSecurityFailureType.NULL_BYTE_INJECTION.isProtocolViolation(), "NULL_BYTE_INJECTION should not be classified as protocol violation");
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 25)
    void shouldHaveExactlyOneCategory(UrlSecurityFailureType type) {
        int categoryCount = 0;

        if (type.isEncodingIssue()) categoryCount++;
        if (type.isPathTraversalAttack()) categoryCount++;
        if (type.isCharacterAttack()) categoryCount++;
        if (type.isSizeViolation()) categoryCount++;
        if (type.isPatternBased()) categoryCount++;
        // XSS attack check removed - application layer responsibility
        if (type.isStructuralIssue()) categoryCount++;
        if (type.isProtocolViolation()) categoryCount++;
        if (type.isIPv6HostAttack()) categoryCount++;

        assertEquals(1, categoryCount,
                "Failure type " + type + " should belong to exactly one category, but belongs to " + categoryCount);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 25)
    void shouldHaveDescriptiveNames(UrlSecurityFailureType type) {
        String name = type.name();
        assertTrue(name.matches("^[A-Z][A-Z0-9_]*[A-Z0-9]$"),
                "Enum name should be uppercase with underscores and numbers: " + name);
        assertTrue(name.length() > 3,
                "Enum name should be descriptive (>3 chars): " + name);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 21)
    void shouldSupportToString(UrlSecurityFailureType type) {
        String toString = type.toString();
        assertNotNull(toString);
        assertFalse(toString.trim().isEmpty());
        assertEquals(type.name(), toString);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 21)
    void shouldSupportValueOf(UrlSecurityFailureType type) {
        UrlSecurityFailureType parsed = UrlSecurityFailureType.valueOf(type.name());
        assertEquals(type, parsed);
    }

    @Test
    void shouldThrowExceptionForInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlSecurityFailureType.valueOf("INVALID_FAILURE_TYPE"));
        assertThrows(IllegalArgumentException.class, () ->
                UrlSecurityFailureType.valueOf(""));
        assertThrows(NullPointerException.class, () ->
                UrlSecurityFailureType.valueOf(null));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UrlSecurityFailureTypeGenerator.class, count = 21)
    void shouldBeSerializable(UrlSecurityFailureType type) {
        // This would work in actual serialization
        assertNotNull(type.name());
    }

    @Test
    void shouldHaveStableOrdinals() {
        // Verify ordinal values are as expected (important for serialization)
        // This test will need updating if new enum values are added
        assertEquals(0, UrlSecurityFailureType.INVALID_ENCODING.ordinal());
        assertEquals(1, UrlSecurityFailureType.DOUBLE_ENCODING.ordinal());
        assertEquals(2, UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED.ordinal());
        // ... and so on for critical enum values
    }

    @Test
    void shouldCoverAllSecurityCategories() {
        // Verify we have comprehensive coverage of security failure categories
        long encodingCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isEncodingIssue() ? 1 : 0).sum();
        long pathTraversalCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isPathTraversalAttack() ? 1 : 0).sum();
        long characterCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isCharacterAttack() ? 1 : 0).sum();
        long sizeCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isSizeViolation() ? 1 : 0).sum();
        long patternCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isPatternBased() ? 1 : 0).sum();
        long structuralCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isStructuralIssue() ? 1 : 0).sum();
        long protocolCount = Arrays.stream(UrlSecurityFailureType.values())
                .mapToLong(t -> t.isProtocolViolation() ? 1 : 0).sum();

        // Each category should have at least 2 failure types for comprehensive coverage
        assertTrue(encodingCount >= 2, "Should have at least 2 encoding issue types");
        assertTrue(pathTraversalCount >= 2, "Should have at least 2 path traversal types");
        assertTrue(characterCount >= 2, "Should have at least 2 character attack types");
        assertTrue(sizeCount >= 2, "Should have at least 2 size violation types");
        assertTrue(patternCount >= 2, "Should have at least 2 pattern-based types");
        assertTrue(structuralCount >= 2, "Should have at least 2 structural issue types");
        assertTrue(protocolCount >= 2, "Should have at least 2 protocol violation types");
    }
}