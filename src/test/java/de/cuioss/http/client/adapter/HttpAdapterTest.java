package de.cuioss.http.client.adapter;

import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void testGetNoHeadersDelegatesToGetWithHeaders() {
        var future = adapter.get();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    @Test
    void testGetBlockingDelegatesToGet() {
        var result = adapter.getBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    @Test
    void testGetBlockingNoHeadersDelegatesToGet() {
        var result = adapter.getBlocking(Map.of("X-Test", "value"));
        assertEquals(testResult, result);
        assertTrue(adapter.getWithHeadersCalled);
    }

    // ========== HEAD TESTS ==========

    @Test
    void testHeadNoHeadersDelegatesToHeadWithHeaders() {
        var future = adapter.head();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.headWithHeadersCalled);
    }

    @Test
    void testHeadBlockingDelegatesToHead() {
        var result = adapter.headBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.headWithHeadersCalled);
    }

    // ========== OPTIONS TESTS ==========

    @Test
    void testOptionsNoHeadersDelegatesToOptionsWithHeaders() {
        var future = adapter.options();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.optionsWithHeadersCalled);
    }

    @Test
    void testOptionsBlockingDelegatesToOptions() {
        var result = adapter.optionsBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.optionsWithHeadersCalled);
    }

    // ========== DELETE TESTS ==========

    @Test
    void testDeleteNoHeadersDelegatesToDeleteWithHeaders() {
        var future = adapter.delete();
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteWithHeadersCalled);
    }

    @Test
    void testDeleteBlockingDelegatesToDelete() {
        var result = adapter.deleteBlocking();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteWithHeadersCalled);
    }

    // ========== POST TESTS ==========

    @Test
    void testPostNoHeadersDelegatesToPostWithHeaders() {
        var future = adapter.post("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.postBodyWithHeadersCalled);
    }

    @Test
    void testPostBlockingDelegatesToPost() {
        var result = adapter.postBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.postBodyWithHeadersCalled);
    }

    @Test
    void testPostWithConverterNoHeadersDelegatesToPostWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.post(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.postConverterWithHeadersCalled);
    }

    // ========== PUT TESTS ==========

    @Test
    void testPutNoHeadersDelegatesToPutWithHeaders() {
        var future = adapter.put("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.putBodyWithHeadersCalled);
    }

    @Test
    void testPutBlockingDelegatesToPut() {
        var result = adapter.putBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.putBodyWithHeadersCalled);
    }

    @Test
    void testPutWithConverterNoHeadersDelegatesToPutWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.put(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.putConverterWithHeadersCalled);
    }

    // ========== PATCH TESTS ==========

    @Test
    void testPatchNoHeadersDelegatesToPatchWithHeaders() {
        var future = adapter.patch("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.patchBodyWithHeadersCalled);
    }

    @Test
    void testPatchBlockingDelegatesToPatch() {
        var result = adapter.patchBlocking("test-body");
        assertEquals(testResult, result);
        assertTrue(adapter.patchBodyWithHeadersCalled);
    }

    @Test
    void testPatchWithConverterNoHeadersDelegatesToPatchWithConverter() {
        var converter = new TestRequestConverter();
        var future = adapter.patch(converter, 123);
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.patchConverterWithHeadersCalled);
    }

    // ========== DELETE WITH BODY TESTS ==========

    @Test
    void testDeleteWithBodyNoHeadersDelegatesToDeleteWithHeaders() {
        var future = adapter.delete("test-body");
        assertNotNull(future);
        var result = future.join();
        assertEquals(testResult, result);
        assertTrue(adapter.deleteBodyWithHeadersCalled);
    }

    @Test
    void testDeleteWithConverterNoHeadersDelegatesToDeleteWithConverter() {
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
        public java.net.http.HttpRequest.BodyPublisher toBodyPublisher(Integer content) {
            return java.net.http.HttpRequest.BodyPublishers.noBody();
        }

        @Override
        public de.cuioss.http.client.ContentType contentType() {
            return de.cuioss.http.client.ContentType.APPLICATION_JSON;
        }
    }
}
