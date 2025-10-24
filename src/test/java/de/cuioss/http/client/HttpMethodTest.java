package de.cuioss.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpMethod} enum.
 *
 * @author CUI-HTTP Development Team
 */
class HttpMethodTest {

    @Test
    void shouldIdentifySafeMethods() {
        // Safe methods: GET, HEAD, OPTIONS
        assertTrue(HttpMethod.GET.isSafe(), "GET should be safe");
        assertTrue(HttpMethod.HEAD.isSafe(), "HEAD should be safe");
        assertTrue(HttpMethod.OPTIONS.isSafe(), "OPTIONS should be safe");

        // Unsafe methods: POST, PUT, DELETE, PATCH
        assertFalse(HttpMethod.POST.isSafe(), "POST should not be safe");
        assertFalse(HttpMethod.PUT.isSafe(), "PUT should not be safe");
        assertFalse(HttpMethod.DELETE.isSafe(), "DELETE should not be safe");
        assertFalse(HttpMethod.PATCH.isSafe(), "PATCH should not be safe");
    }

    @Test
    void shouldIdentifyIdempotentMethods() {
        // Idempotent methods: GET, PUT, DELETE, HEAD, OPTIONS
        assertTrue(HttpMethod.GET.isIdempotent(), "GET should be idempotent");
        assertTrue(HttpMethod.PUT.isIdempotent(), "PUT should be idempotent");
        assertTrue(HttpMethod.DELETE.isIdempotent(), "DELETE should be idempotent");
        assertTrue(HttpMethod.HEAD.isIdempotent(), "HEAD should be idempotent");
        assertTrue(HttpMethod.OPTIONS.isIdempotent(), "OPTIONS should be idempotent");

        // Non-idempotent methods: POST, PATCH
        assertFalse(HttpMethod.POST.isIdempotent(), "POST should not be idempotent");
        assertFalse(HttpMethod.PATCH.isIdempotent(), "PATCH should not be idempotent");
    }

    @Test
    void shouldReturnCorrectMethodNames() {
        assertEquals("GET", HttpMethod.GET.methodName());
        assertEquals("POST", HttpMethod.POST.methodName());
        assertEquals("PUT", HttpMethod.PUT.methodName());
        assertEquals("DELETE", HttpMethod.DELETE.methodName());
        assertEquals("PATCH", HttpMethod.PATCH.methodName());
        assertEquals("HEAD", HttpMethod.HEAD.methodName());
        assertEquals("OPTIONS", HttpMethod.OPTIONS.methodName());
    }

    @Test
    void shouldHaveAllMethodsUppercase() {
        for (HttpMethod method : HttpMethod.values()) {
            String methodName = method.methodName();
            assertEquals(methodName.toUpperCase(), methodName,
                    "Method name should be uppercase: " + method);
        }
    }

    @Test
    void getMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.GET;
        assertTrue(method.isSafe(), "GET should be safe");
        assertTrue(method.isIdempotent(), "GET should be idempotent");
        assertEquals("GET", method.methodName());
    }

    @Test
    void postMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.POST;
        assertFalse(method.isSafe(), "POST should not be safe");
        assertFalse(method.isIdempotent(), "POST should not be idempotent");
        assertEquals("POST", method.methodName());
    }

    @Test
    void putMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.PUT;
        assertFalse(method.isSafe(), "PUT should not be safe");
        assertTrue(method.isIdempotent(), "PUT should be idempotent");
        assertEquals("PUT", method.methodName());
    }

    @Test
    void deleteMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.DELETE;
        assertFalse(method.isSafe(), "DELETE should not be safe");
        assertTrue(method.isIdempotent(), "DELETE should be idempotent");
        assertEquals("DELETE", method.methodName());
    }

    @Test
    void patchMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.PATCH;
        assertFalse(method.isSafe(), "PATCH should not be safe");
        assertFalse(method.isIdempotent(), "PATCH should not be idempotent");
        assertEquals("PATCH", method.methodName());
    }

    @Test
    void headMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.HEAD;
        assertTrue(method.isSafe(), "HEAD should be safe");
        assertTrue(method.isIdempotent(), "HEAD should be idempotent");
        assertEquals("HEAD", method.methodName());
    }

    @Test
    void optionsMethodShouldHaveCorrectProperties() {
        HttpMethod method = HttpMethod.OPTIONS;
        assertTrue(method.isSafe(), "OPTIONS should be safe");
        assertTrue(method.isIdempotent(), "OPTIONS should be idempotent");
        assertEquals("OPTIONS", method.methodName());
    }

    @Test
    void shouldHaveSevenHttpMethods() {
        // Verify we have exactly the 7 standard HTTP methods
        HttpMethod[] methods = HttpMethod.values();
        assertEquals(7, methods.length, "Should have exactly 7 HTTP methods");
    }

    @Test
    void allSafeMethodsShouldBeIdempotent() {
        // Per RFC 7231: Safe methods are always idempotent
        for (HttpMethod method : HttpMethod.values()) {
            if (method.isSafe()) {
                assertTrue(method.isIdempotent(),
                        "Safe method should be idempotent: " + method);
            }
        }
    }

    @Test
    void shouldIdentifyNonIdempotentMethodsForRetryDecisions() {
        // Critical for retry logic: only POST and PATCH should be non-idempotent
        int nonIdempotentCount = 0;
        for (HttpMethod method : HttpMethod.values()) {
            if (!method.isIdempotent()) {
                nonIdempotentCount++;
                assertTrue(method == HttpMethod.POST || method == HttpMethod.PATCH,
                        "Only POST and PATCH should be non-idempotent, found: " + method);
            }
        }
        assertEquals(2, nonIdempotentCount,
                "Should have exactly 2 non-idempotent methods (POST, PATCH)");
    }

    @Test
    void shouldIdentifySafeMethodsForBodyValidation() {
        // Critical for body validation: GET, HEAD, OPTIONS should not have bodies
        int safeCount = 0;
        for (HttpMethod method : HttpMethod.values()) {
            if (method.isSafe()) {
                safeCount++;
                assertTrue(
                        method == HttpMethod.GET ||
                        method == HttpMethod.HEAD ||
                        method == HttpMethod.OPTIONS,
                        "Only GET, HEAD, OPTIONS should be safe, found: " + method);
            }
        }
        assertEquals(3, safeCount, "Should have exactly 3 safe methods");
    }
}
