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
package de.cuioss.http.client.result;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link HttpErrorCategory} enum to verify the simplified essential error codes.
 * Tests only the behavioral method that matters: isRetryable().
 */
class HttpErrorCategoryTest {

    @Test
    void shouldIdentifyRetryableErrors() {
        // Retryable errors - transient conditions
        assertTrue(HttpErrorCategory.NETWORK_ERROR.isRetryable());
        assertTrue(HttpErrorCategory.SERVER_ERROR.isRetryable());

        // Non-retryable errors - permanent conditions
        assertFalse(HttpErrorCategory.CLIENT_ERROR.isRetryable());
        assertFalse(HttpErrorCategory.INVALID_CONTENT.isRetryable());
        assertFalse(HttpErrorCategory.CONFIGURATION_ERROR.isRetryable());
    }

    @Test
    void shouldHaveMinimalButSufficientStates() {
        // Verify we have exactly the essential states
        HttpErrorCategory[] allCodes = HttpErrorCategory.values();
        assertEquals(5, allCodes.length, "Should have exactly 5 essential error codes");

        // Verify all expected codes exist
        assertNotNull(HttpErrorCategory.valueOf("NETWORK_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("SERVER_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("CLIENT_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("INVALID_CONTENT"));
        assertNotNull(HttpErrorCategory.valueOf("CONFIGURATION_ERROR"));
    }

    @Test
    void shouldProvideSemanticClarityThroughNaming() {
        // Error types are self-explanatory through enum names
        // No need for additional classification methods
        for (HttpErrorCategory errorCode : HttpErrorCategory.values()) {
            assertNotNull(errorCode);
            assertNotNull(errorCode.name());

            // All enum names should be descriptive (either ending with _ERROR or _CONTENT)
            boolean hasValidSuffix = errorCode.name().endsWith("_ERROR") ||
                    errorCode.name().endsWith("_CONTENT");
            assertTrue(hasValidSuffix,
                    "Error code " + errorCode + " should have descriptive suffix");
        }
    }

    @Test
    void shouldCorrectlyIdentifyRetryableErrors() {
        var retryable = Arrays.stream(HttpErrorCategory.values())
                .filter(HttpErrorCategory::isRetryable)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(HttpErrorCategory.NETWORK_ERROR, HttpErrorCategory.SERVER_ERROR), retryable,
                "Only NETWORK_ERROR and SERVER_ERROR should be retryable.");
    }

    @Test
    void shouldCorrectlyIdentifyNonRetryableErrors() {
        var nonRetryable = Arrays.stream(HttpErrorCategory.values())
                .filter(c -> !c.isRetryable())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(HttpErrorCategory.CLIENT_ERROR, HttpErrorCategory.INVALID_CONTENT, HttpErrorCategory.CONFIGURATION_ERROR), nonRetryable,
                "CLIENT_ERROR, INVALID_CONTENT, and CONFIGURATION_ERROR should be non-retryable.");
    }
}