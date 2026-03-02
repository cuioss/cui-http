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
package de.cuioss.http.client.converter;

import de.cuioss.http.client.ContentType;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VoidResponseConverter}.
 */
class VoidResponseConverterTest {

    /**
     * Test that INSTANCE field is accessible and not null.
     */
    @Test
    void shouldProvideSingletonInstance() {
        assertNotNull(VoidResponseConverter.INSTANCE);
    }

    /**
     * Test that multiple references to INSTANCE are the same object.
     */
    @Test
    void shouldReturnSameSingletonInstance() {
        VoidResponseConverter first = VoidResponseConverter.INSTANCE;
        VoidResponseConverter second = VoidResponseConverter.INSTANCE;
        assertSame(first, second, "INSTANCE should return same object");
    }

    /**
     * Test that convert() always returns Optional.empty() regardless of input.
     */
    @Test
    void shouldAlwaysReturnEmptyOptional() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        // Test with null
        Optional<Void> result1 = converter.convert(null);
        assertTrue(result1.isEmpty(), "Should return empty Optional for null");

        // Test with String
        Optional<Void> result2 = converter.convert("some content");
        assertTrue(result2.isEmpty(), "Should return empty Optional for String");

        // Test with byte array
        Optional<Void> result3 = converter.convert(new byte[]{1, 2, 3});
        assertTrue(result3.isEmpty(), "Should return empty Optional for byte array");

        // Test with Object
        Optional<Void> result4 = converter.convert(new Object());
        assertTrue(result4.isEmpty(), "Should return empty Optional for Object");
    }

    /**
     * Test that getBodyHandler() returns a discarding handler.
     * We verify it's not null and is the correct type.
     */
    @Test
    void shouldReturnDiscardingBodyHandler() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;
        HttpResponse.BodyHandler<?> handler = converter.getBodyHandler();

        assertNotNull(handler, "Body handler should not be null");

        // The handler should be a discarding handler
        // We can verify this by checking that it's the same type as the one we expect
        HttpResponse.BodyHandler<?> expectedHandler = HttpResponse.BodyHandlers.discarding();
        assertEquals(expectedHandler.getClass(), handler.getClass(),
                "Should return discarding body handler");
    }

    /**
     * Test that contentType() returns APPLICATION_JSON.
     * Note: The actual value doesn't matter since body is discarded,
     * but we test that it returns a valid ContentType.
     */
    @Test
    void shouldReturnContentType() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;
        ContentType contentType = converter.contentType();

        assertNotNull(contentType, "Content type should not be null");
        assertEquals(ContentType.APPLICATION_JSON, contentType,
                "Should return APPLICATION_JSON as default (body discarded anyway)");
    }

    /**
     * Test that the converter implements HttpResponseConverter interface correctly.
     */
    @Test
    void shouldImplementHttpResponseConverterInterface() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;
        assertInstanceOf(HttpResponseConverter.class, converter, "Should implement HttpResponseConverter interface");
    }

    /**
     * Test convert() method signature and return type.
     */
    @Test
    void shouldHaveCorrectConvertSignature() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        // Verify that convert returns Optional<Void> not Optional<Object>
        Optional<Void> result = converter.convert("test");
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty");

        // Type parameter is correctly Void
        assertEquals(Optional.empty(), result, "Should equal Optional.empty()");
    }

    /**
     * Test that body handler is reusable and consistent.
     */
    @Test
    void shouldReturnConsistentBodyHandler() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        HttpResponse.BodyHandler<?> handler1 = converter.getBodyHandler();
        HttpResponse.BodyHandler<?> handler2 = converter.getBodyHandler();

        assertNotNull(handler1, "First handler should not be null");
        assertNotNull(handler2, "Second handler should not be null");
        assertEquals(handler1.getClass(), handler2.getClass(),
                "Should return same type of handler consistently");
    }

    /**
     * Test that contentType is consistent across calls.
     */
    @Test
    void shouldReturnConsistentContentType() {
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        ContentType type1 = converter.contentType();
        ContentType type2 = converter.contentType();

        assertSame(type1, type2, "Should return same ContentType enum value");
    }

    /**
     * Test practical usage scenario: status-code-only operation.
     */
    @Test
    void shouldSupportStatusCodeOnlyUsagePattern() {
        // Simulate DELETE operation where only status code matters
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        // Convert the (discarded) response body
        Optional<Void> content = converter.convert(null);

        // Verify we get empty as expected
        assertTrue(content.isEmpty(),
                "DELETE operation should return empty content - only status matters");

        // Verify body handler is efficient (discarding)
        HttpResponse.BodyHandler<?> handler = converter.getBodyHandler();
        assertNotNull(handler, "Should have body handler for HTTP operation");
    }

    /**
     * Test practical usage scenario: HEAD operation.
     */
    @Test
    void shouldSupportHeadOperationPattern() {
        // Simulate HEAD operation where only headers/status matter
        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;

        // HEAD responses have no body, so we expect empty
        Optional<Void> content = converter.convert(null);

        assertTrue(content.isEmpty(),
                "HEAD operation should return empty content - only headers/status matter");

        // Verify efficient body handling
        HttpResponse.BodyHandler<?> handler = converter.getBodyHandler();
        assertEquals(HttpResponse.BodyHandlers.discarding().getClass(), handler.getClass(),
                "Should use efficient discarding handler for HEAD");
    }

    /**
     * Test that the converter is thread-safe (singleton usage).
     */
    @Test
    void shouldBeThreadSafe() throws Exception {
        // Since INSTANCE is a singleton with no mutable state,
        // it should be inherently thread-safe

        VoidResponseConverter converter = VoidResponseConverter.INSTANCE;
        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];

        // Create multiple threads that all use the converter
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                Optional<Void> result = converter.convert("test");
                results[index] = result.isEmpty();
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all threads got the expected result
        for (int i = 0; i < results.length; i++) {
            assertTrue(results[i], "Thread " + i + " should return empty Optional");
        }
    }
}
