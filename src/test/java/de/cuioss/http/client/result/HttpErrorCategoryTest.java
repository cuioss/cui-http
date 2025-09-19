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
    void shouldHaveConsistentRetrySemantics() {
        // Network and server errors are the only transient/retryable conditions
        int retryableCount = 0;
        int nonRetryableCount = 0;

        for (HttpErrorCategory errorCode : HttpErrorCategory.values()) {
            if (errorCode.isRetryable()) {
                retryableCount++;
                // Only these two should be retryable
                assertTrue(errorCode == HttpErrorCategory.NETWORK_ERROR ||
                        errorCode == HttpErrorCategory.SERVER_ERROR,
                        "Only NETWORK_ERROR and SERVER_ERROR should be retryable");
            } else {
                nonRetryableCount++;
            }
        }

        assertEquals(2, retryableCount, "Should have exactly 2 retryable error types");
        assertEquals(3, nonRetryableCount, "Should have exactly 3 non-retryable error types");
    }
}