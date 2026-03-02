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
package de.cuioss.http.client.adapter;

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpAdapter} interface default methods.
 * Tests that blocking methods correctly delegate to async methods.
 */
class HttpAdapterTest {

    private TestHttpAdapter adapter;
    private HttpResult<String> testResult;

    @BeforeEach
    void setUp() {
        testResult = HttpResult.success("test-content", "test-etag", 200);
        adapter = new TestHttpAdapter(testResult);
    }

    // ========== GET TESTS ==========

    @Test
    void getNoHeadersDelegatesToGetWithHeaders() {
        var future = adapter.get();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    @Test
    void getBlockingDelegatesToGet() {
        var result = adapter.getBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    @Test
    void getBlockingNoHeadersDelegatesToGet() {
        var result = adapter.getBlocking(Map.of("X-Test", "value"));
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    // ========== HEAD TESTS ==========

    @Test
    void headNoHeadersDelegatesToHeadWithHeaders() {
        var future = adapter.head();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.headWithHeadersCalled);
    }

    @Test
    void headBlockingDelegatesToHead() {
        var result = adapter.headBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.headWithHeadersCalled);
    }

    // ========== OPTIONS TESTS ==========

    @Test
    void optionsNoHeadersDelegatesToOptionsWithHeaders() {
        var future = adapter.options();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.optionsWithHeadersCalled);
    }

    @Test
    void optionsBlockingDelegatesToOptions() {
        var result = adapter.optionsBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.optionsWithHeadersCalled);
    }

    // ========== DELETE TESTS ==========

    @Test
    void deleteNoHeadersDelegatesToDeleteWithHeaders() {
        var future = adapter.delete();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteWithHeadersCalled);
    }

    @Test
    void deleteBlockingDelegatesToDelete() {
        var result = adapter.deleteBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteWithHeadersCalled);
    }

    // ========== POST TESTS ==========

    @Test
    void postNoHeadersDelegatesToPostWithHeaders() {
        var future = adapter.post("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.postBodyWithHeadersCalled);
    }

    @Test
    void postBlockingDelegatesToPost() {
        var result = adapter.postBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.postBodyWithHeadersCalled);
    }

    @Test
    void postWithConverterNoHeadersDelegatesToPostWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.post(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.postConverterWithHeadersCalled);
    }

    // ========== PUT TESTS ==========

    @Test
    void putNoHeadersDelegatesToPutWithHeaders() {
        var future = adapter.put("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.putBodyWithHeadersCalled);
    }

    @Test
    void putBlockingDelegatesToPut() {
        var result = adapter.putBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.putBodyWithHeadersCalled);
    }

    @Test
    void putWithConverterNoHeadersDelegatesToPutWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.put(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.putConverterWithHeadersCalled);
    }

    // ========== PATCH TESTS ==========

    @Test
    void patchNoHeadersDelegatesToPatchWithHeaders() {
        var future = adapter.patch("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.patchBodyWithHeadersCalled);
    }

    @Test
    void patchBlockingDelegatesToPatch() {
        var result = adapter.patchBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.patchBodyWithHeadersCalled);
    }

    @Test
    void patchWithConverterNoHeadersDelegatesToPatchWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.patch(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.patchConverterWithHeadersCalled);
    }

    // ========== DELETE WITH BODY TESTS ==========

    @Test
    void deleteWithBodyNoHeadersDelegatesToDeleteWithHeaders() {
        var future = adapter.delete("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteBodyWithHeadersCalled);
    }

    @Test
    void deleteWithConverterNoHeadersDelegatesToDeleteWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.delete(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteConverterWithHeadersCalled);
    }

    /**
     * Test implementation of HttpAdapter that tracks method invocations.
     */
    private static class TestHttpAdapter implements HttpAdapter<String> {
        private final HttpResult<String> result;

        // Tracking flags
        boolean getWithHeadersCalled = false;
        boolean headWithHeadersCalled = false;
        boolean optionsWithHeadersCalled = false;
        boolean deleteWithHeadersCalled = false;
        boolean postBodyWithHeadersCalled = false;
        boolean putBodyWithHeadersCalled = false;
        boolean patchBodyWithHeadersCalled = false;
        boolean deleteBodyWithHeadersCalled = false;
        boolean postConverterWithHeadersCalled = false;
        boolean putConverterWithHeadersCalled = false;
        boolean patchConverterWithHeadersCalled = false;
        boolean deleteConverterWithHeadersCalled = false;

        TestHttpAdapter(HttpResult<String> result) {
            this.result = result;
        }

        @Override
        public CompletableFuture<HttpResult<String>> get(Map<String, String> additionalHeaders) {
            getWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> head(Map<String, String> additionalHeaders) {
            headWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> options(Map<String, String> additionalHeaders) {
            optionsWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> delete(Map<String, String> additionalHeaders) {
            deleteWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> post(String requestBody, Map<String, String> additionalHeaders) {
            postBodyWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> put(String requestBody, Map<String, String> additionalHeaders) {
            putBodyWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> patch(String requestBody, Map<String, String> additionalHeaders) {
            patchBodyWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<HttpResult<String>> delete(String requestBody, Map<String, String> additionalHeaders) {
            deleteBodyWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public <R> CompletableFuture<HttpResult<String>> post(HttpRequestConverter<R> requestConverter,
                R requestBody,
                Map<String, String> additionalHeaders) {
            postConverterWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public <R> CompletableFuture<HttpResult<String>> put(HttpRequestConverter<R> requestConverter,
                R requestBody,
                Map<String, String> additionalHeaders) {
            putConverterWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public <R> CompletableFuture<HttpResult<String>> patch(HttpRequestConverter<R> requestConverter,
                R requestBody,
                Map<String, String> additionalHeaders) {
            patchConverterWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public <R> CompletableFuture<HttpResult<String>> delete(HttpRequestConverter<R> requestConverter,
                R requestBody,
                Map<String, String> additionalHeaders) {
            deleteConverterWithHeadersCalled = true;
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Test request converter for generic method tests.
     */
    private static class TestRequestConverter implements HttpRequestConverter<Integer> {
        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(Integer content) {
            return HttpRequest.BodyPublishers.noBody();
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }
}
