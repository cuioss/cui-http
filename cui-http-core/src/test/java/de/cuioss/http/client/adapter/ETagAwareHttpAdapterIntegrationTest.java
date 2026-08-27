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
package de.cuioss.http.client.adapter;

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.HttpMethod;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ETagAwareHttpAdapter} using MockWebServer.
 * <p>
 * Tests realistic HTTP scenarios:
 * <ul>
 *   <li>GET with ETag caching (200 → 304 flow)</li>
 *   <li>POST/PUT/DELETE with body handling</li>
 *   <li>Network failures and error handling</li>
 *   <li>Server errors (5xx) and client errors (4xx)</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("ETagAwareHttpAdapter Integration Tests")
class ETagAwareHttpAdapterIntegrationTest {

    private static final String SEED_CONTENT = "{\"id\":1,\"name\":\"seeded\"}";
    private static final String SEED_ETAG = "\"etag-seed\"";
    private static final Map<String, String> CONDITIONAL_HEADERS = Map.of("If-None-Match", SEED_ETAG);

    private final TestApiDispatcher dispatcher = new TestApiDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    /**
     * Test GET with ETag: first request 200 with ETag, second request sends If-None-Match, receives 304, returns Success with cached content
     */
    @Test
    @DisplayName("GET with ETag should cache and return 304 on second request")
    @ModuleDispatcher
    void getWithETagShouldCacheAndReturn304OnSecondRequest(URIBuilder uriBuilder) {
        // Configure dispatcher to return 200 with ETag, then 304
        dispatcher.withSuccessAndETag("{\"id\":1,\"name\":\"test\"}", "\"etag-123\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        // First request: 200 OK with ETag
        HttpResult<String> result1 = adapter.getBlocking();
        assertTrue(result1.isSuccess(), "First request should succeed");
        assertEquals("{\"id\":1,\"name\":\"test\"}", result1.getContent().orElse(null));
        assertEquals("\"etag-123\"", result1.getETag().orElse(null));
        assertEquals(Optional.of(200), result1.getHttpStatus());

        // Configure dispatcher to return 304 for second request
        dispatcher.with304();

        // Second request: 304 Not Modified (cached content returned)
        HttpResult<String> result2 = adapter.getBlocking();
        assertTrue(result2.isSuccess(), "Second request should succeed with cached content");
        assertEquals("{\"id\":1,\"name\":\"test\"}", result2.getContent().orElse(null), "Should return cached content");
        assertEquals("\"etag-123\"", result2.getETag().orElse(null), "Should preserve cached ETag");
        assertEquals(Optional.of(304), result2.getHttpStatus(), "Status code should be 304");

        // Verify If-None-Match header was sent on second request
        assertTrue(dispatcher.getLastIfNoneMatch().isPresent(), "If-None-Match header should be sent");
        assertEquals("\"etag-123\"", dispatcher.getLastIfNoneMatch().orElse(null));
    }

    @Test
    @DisplayName("A 304 to HEAD should report status and ETag with no body")
    @ModuleDispatcher
    void head304ShouldReportStatusAndETagWithoutBody(URIBuilder uriBuilder) {
        dispatcher.withSeedThen304(SEED_CONTENT, SEED_ETAG);
        HttpAdapter<String> adapter = matrixAdapter(uriBuilder);
        assertTrue(adapter.getBlocking().isSuccess(), "Seeding GET should succeed and populate the cache");

        HttpResult<String> result = adapter.head(CONDITIONAL_HEADERS).join();

        assertAll("HEAD revalidated with 304",
                () -> assertTrue(result.isSuccess(), "A 304 to HEAD is a valid revalidation"),
                () -> assertEquals(Optional.of(304), result.getHttpStatus(), "Status should be reported as 304"),
                () -> assertEquals(SEED_ETAG, result.getETag().orElse(null), "The validator should be reported"),
                () -> assertTrue(result.getContent().isEmpty(), "A HEAD response carries no body"));
    }

    /**
     * A 304 answering an unsafe method is a server protocol violation (RFC 7232 mandates 412), so it
     * must surface as a failure that leaks neither the cached body nor the cached validator.
     * <p>
     * The message assertion is what makes this test verify the unsafe-method gate rather than a
     * coincidence. An unsafe method never holds a cache entry, so a 304 that is not resolved by the
     * dedicated gate falls through to the generic error path — which yields {@code INVALID_CONTENT},
     * status 304 and no fallback content all by itself, satisfying every other assertion here. Only
     * the RFC-specific message distinguishes the two paths, so this case fails if the gate is
     * bypassed.
     * <p>
     * PATCH and OPTIONS belong to this equivalence class and are gated identically in production,
     * but cannot be exercised here: the MockWebServer fixture cannot serve them yet.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = HttpMethod.class, names = {"POST", "PUT", "DELETE"})
    @DisplayName("A 304 to an unsafe method should fail without leaking cached content or ETag")
    @ModuleDispatcher
    void unsafeMethod304ShouldFailWithoutLeakingCachedData(HttpMethod method, URIBuilder uriBuilder) {
        dispatcher.withSeedThen304(SEED_CONTENT, SEED_ETAG);
        HttpAdapter<String> adapter = matrixAdapter(uriBuilder);
        assertTrue(adapter.getBlocking().isSuccess(), "Seeding GET should succeed and populate the cache");

        HttpResult<String> result = revalidateWith(adapter, method);

        assertAll("%s revalidated with 304".formatted(method.methodName()),
                () -> assertFalse(result.isSuccess(), "A 304 to an unsafe method is a protocol violation"),
                () -> assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElse(null),
                        "The violation should be categorised as invalid content"),
                () -> assertEquals(Optional.of(304), result.getHttpStatus(), "The observed status should be preserved"),
                () -> assertEquals(
                        "HTTP 304 is not a valid response to a %s request (RFC 7232 requires 412)".formatted(method.methodName()),
                        result.getErrorMessage().orElse(null),
                        "The failure must name the RFC 7232 violation - the generic error path would report 'HTTP 304: %s' instead"
                                .formatted(method.methodName())),
                () -> assertTrue(result.getContent().isEmpty(), "The cached GET body must not leak"),
                () -> assertTrue(result.getETag().isEmpty(), "The cached GET validator must not leak"));
    }

    /**
     * A 304 on a safe method with no cached entry is equally unresolvable: the adapter only issues a
     * conditional request when it holds a validator, so an unsolicited 304 has nothing to be resolved
     * against. It must fail as {@code INVALID_CONTENT} rather than surface as an empty success.
     */
    @Test
    @DisplayName("A 304 with no cached entry should fail rather than yield an empty success")
    @ModuleDispatcher
    void unsolicited304WithoutCachedEntryShouldFail(URIBuilder uriBuilder) {
        dispatcher.with304();
        HttpAdapter<String> adapter = matrixAdapter(uriBuilder);

        HttpResult<String> result = adapter.getBlocking();

        assertAll("GET answered with an unsolicited 304",
                () -> assertFalse(result.isSuccess(), "A 304 with nothing to resolve it against is not a success"),
                () -> assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElse(null),
                        "The unresolvable 304 should be categorised as invalid content"),
                () -> assertEquals(Optional.of(304), result.getHttpStatus(), "The observed status should be preserved"),
                () -> assertEquals(
                        "HTTP 304 for a GET request with no cached entry to resolve it against (RFC 7232 permits 304 only in response to a conditional request the adapter issued)",
                        result.getErrorMessage().orElse(null),
                        "The failure must name why the 304 could not be resolved"),
                () -> assertTrue(result.getContent().isEmpty(), "No content should be fabricated"),
                () -> assertTrue(result.getETag().isEmpty(), "No validator should be fabricated"));
    }

    /**
     * Builds the adapter used by the 304 matrix. The cache key is URI-only so that the
     * caller-supplied {@code If-None-Match} on the revalidating request resolves to the very entry
     * the seeding GET stored — with the default {@code ALL} filter the extra header would produce a
     * different key and the 304 branch would never be reached.
     */
    private HttpAdapter<String> matrixAdapter(URIBuilder uriBuilder) {
        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        return ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)
                .build();
    }

    /**
     * Issues a body-less request under {@code method} carrying a caller-supplied
     * {@code If-None-Match} — the only way a non-GET request can draw a 304 from a conformant server.
     */
    private HttpResult<String> revalidateWith(HttpAdapter<String> adapter, HttpMethod method) {
        return switch (method) {
            case POST -> adapter.post((String) null, CONDITIONAL_HEADERS).join();
            case PUT -> adapter.put((String) null, CONDITIONAL_HEADERS).join();
            case DELETE -> adapter.delete(CONDITIONAL_HEADERS).join();
            default -> throw new IllegalArgumentException(
                    "The 304 matrix does not drive " + method.methodName());
        };
    }

    @Test
    @DisplayName("A 204 should succeed even when the converter yields no content")
    @ModuleDispatcher
    void noContent204WithTypedConverterShouldSucceed(URIBuilder uriBuilder) {
        dispatcher.withNoContent();
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        HttpResult<String> result = adapter.deleteBlocking();

        assertAll("DELETE answered with 204",
                () -> assertTrue(result.isSuccess(), "204 carries no body by definition, so emptiness is not a conversion failure"),
                () -> assertEquals(Optional.of(204), result.getHttpStatus()),
                () -> assertTrue(result.getErrorCategory().isEmpty(), "Success must not carry an error category"));
    }

    @Test
    @DisplayName("A 205 should succeed even when the converter yields no content")
    @ModuleDispatcher
    void resetContent205WithTypedConverterShouldSucceed(URIBuilder uriBuilder) {
        dispatcher.withNoContentAndStatus(205);
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        HttpResult<String> result = adapter.put((String) null).join();

        assertAll("PUT answered with 205",
                () -> assertTrue(result.isSuccess(), "205 carries no body by definition, so emptiness is not a conversion failure"),
                () -> assertEquals(Optional.of(205), result.getHttpStatus()),
                () -> assertTrue(result.getErrorCategory().isEmpty(), "Success must not carry an error category"));
    }

    /**
     * The non-widening control for the no-body exemption: an empty {@code 200} is not a
     * protocol-defined no-body response, so it must keep failing unless the converter opts in via
     * {@code emptyContentIsValid()}. Without this case the exemption could silently widen to the
     * whole 2xx family and swallow genuine conversion failures.
     */
    @Test
    @DisplayName("An empty 200 should still fail as INVALID_CONTENT")
    @ModuleDispatcher
    void empty200WithTypedConverterShouldStillFail(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("", "\"etag-empty-200\"");
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        HttpResult<String> result = adapter.getBlocking();

        assertAll("GET answered with an empty 200",
                () -> assertFalse(result.isSuccess(), "200 is not a no-body status - the exemption must not widen to it"),
                () -> assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElse(null)),
                () -> assertTrue(result.getContent().isEmpty(), "No content should be fabricated"));
    }

    @Test
    @DisplayName("A conversion failure should preserve the HTTP status and the response ETag")
    @ModuleDispatcher
    void conversionFailureShouldPreserveStatusAndETag(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag(TypedResponseConverter.UNPARSEABLE_BODY, "\"etag-unparseable\"");
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        HttpResult<String> result = adapter.getBlocking();

        assertAll("200 whose body the converter rejects",
                () -> assertFalse(result.isSuccess(), "An unparseable body is a conversion failure"),
                () -> assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElse(null)),
                () -> assertEquals(Optional.of(200), result.getHttpStatus(),
                        "The status was in hand and must not be discarded"),
                () -> assertEquals("\"etag-unparseable\"", result.getETag().orElse(null),
                        "The response ETag was in hand and must not be discarded"),
                () -> assertTrue(result.getContent().isEmpty(), "No fallback content should be fabricated"));
    }

    /**
     * An ETag-less 200 means the server returned content it no longer identifies by any validator,
     * so the previously cached entry is provably stale and must go. Two independent observables pin
     * the same invariant, so the case cannot pass by accident: the later failure carries no
     * fallback content, and the following request sends no stale {@code If-None-Match}.
     */
    @Test
    @DisplayName("An ETag-less 200 should invalidate the cached entry")
    @ModuleDispatcher
    void etagLess200ShouldInvalidateCachedEntry(URIBuilder uriBuilder) {
        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();
        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        dispatcher.withSuccessAndETag(SEED_CONTENT, SEED_ETAG);
        assertTrue(adapter.getBlocking().isSuccess(), "Seeding GET should succeed and populate the cache");

        dispatcher.withSuccessAndETag("{\"id\":1,\"name\":\"unvalidated\"}", null);
        assertTrue(adapter.getBlocking().isSuccess(), "The ETag-less GET should still succeed");

        dispatcher.withServerError();
        HttpResult<String> failure = adapter.getBlocking();

        assertAll("Failure after an ETag-less 200 evicted the entry",
                () -> assertFalse(failure.isSuccess(), "503 should surface as a failure"),
                () -> assertTrue(failure.getContent().isEmpty(),
                        "The evicted entry must not be served as fallback content"),
                () -> assertTrue(dispatcher.getLastIfNoneMatch().isEmpty(),
                        "No stale If-None-Match should be sent once the entry is gone"));
    }

    /**
     * The converter-blind half of the same invalidation invariant. {@link StringResponseConverter}
     * maps an empty body to {@code Optional.of("")}, so the sibling case above never reaches the
     * conversion-failure return and cannot observe whether eviction happens before or after it. A
     * typed converter that rejects the body does return early, so this sequence pins the ordering:
     * an ETag-less 200 must drop the superseded entry even when its body is unparseable, or the
     * following 503 serves content the 200 already replaced.
     */
    @Test
    @DisplayName("An ETag-less 200 should invalidate the cached entry even when conversion fails")
    @ModuleDispatcher
    void etagLess200WithFailedConversionShouldInvalidateCachedEntry(URIBuilder uriBuilder) {
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        dispatcher.withSuccessAndETag(SEED_CONTENT, SEED_ETAG);
        assertTrue(adapter.getBlocking().isSuccess(), "Seeding GET should succeed and populate the cache");

        dispatcher.withSuccessAndETag(TypedResponseConverter.UNPARSEABLE_BODY, null);
        HttpResult<String> unparseable = adapter.getBlocking();
        assertFalse(unparseable.isSuccess(), "An unparseable body is a conversion failure");

        dispatcher.withServerError();
        HttpResult<String> failure = adapter.getBlocking();

        assertAll("Failure after an ETag-less, unparseable 200 evicted the entry",
                () -> assertFalse(failure.isSuccess(), "503 should surface as a failure"),
                () -> assertTrue(failure.getContent().isEmpty(),
                        "The evicted entry must not be served as fallback content"),
                () -> assertTrue(failure.getETag().isEmpty(),
                        "The evicted entry's validator must not be served either"),
                () -> assertTrue(dispatcher.getLastIfNoneMatch().isEmpty(),
                        "No stale If-None-Match should be sent once the entry is gone"));
    }

    /**
     * The validator-bearing half of the invalidation invariant, and the case an eviction gated on a
     * missing ETag misses entirely. A {@code 200} carrying an ETag whose body the converter rejects
     * yields no content, so no replacement entry can be built from it — yet it is still a fresh
     * representation that supersedes the cached one. What makes the old entry stale is that the
     * fresh {@code 200} could not replace it, not the reason it could not, so this sequence must
     * evict exactly as the ETag-less sibling above does.
     * <p>
     * Three independent observables pin it, and all three are wrong when the eviction is skipped:
     * the later failure carries neither the superseded body nor the superseded validator, and the
     * request that draws it sends no stale {@code If-None-Match}. The intermediate assertion that
     * the conversion failure reports the <em>fresh</em> ETag rather than the seeded one keeps the
     * scenario honest — it confirms the second response really did carry its own validator, so the
     * case exercises the ETag-bearing branch and not the ETag-less one.
     */
    @Test
    @DisplayName("An ETag-bearing 200 whose body is unparseable should invalidate the cached entry")
    @ModuleDispatcher
    void unparseable200WithETagShouldInvalidateCachedEntry(URIBuilder uriBuilder) {
        HttpAdapter<String> adapter = typedAdapter(uriBuilder);

        dispatcher.withSuccessAndETag(SEED_CONTENT, SEED_ETAG);
        assertTrue(adapter.getBlocking().isSuccess(), "Seeding GET should succeed and populate the cache");

        dispatcher.withSuccessAndETag(TypedResponseConverter.UNPARSEABLE_BODY, "\"etag-fresh\"");
        HttpResult<String> unparseable = adapter.getBlocking();

        assertAll("200 carrying an ETag whose body the converter rejects",
                () -> assertFalse(unparseable.isSuccess(), "An unparseable body is a conversion failure"),
                () -> assertEquals("\"etag-fresh\"", unparseable.getETag().orElse(null),
                        "The response's own validator should be reported - proving this is the ETag-bearing branch"));

        dispatcher.withServerError();
        HttpResult<String> failure = adapter.getBlocking();

        assertAll("Failure after an ETag-bearing, unparseable 200 evicted the entry",
                () -> assertFalse(failure.isSuccess(), "503 should surface as a failure"),
                () -> assertTrue(failure.getContent().isEmpty(),
                        "The superseded entry must not be served as fallback content"),
                () -> assertTrue(failure.getETag().isEmpty(),
                        "The superseded entry's validator must not be served either"),
                () -> assertTrue(dispatcher.getLastIfNoneMatch().isEmpty(),
                        "No stale If-None-Match should be sent once the entry is gone"));
    }

    /**
     * Builds an adapter over {@link TypedResponseConverter} — the converter shape that can actually
     * reach the conversion-failure branch.
     */
    private HttpAdapter<String> typedAdapter(URIBuilder uriBuilder) {
        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        return ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new TypedResponseConverter())
                .build();
    }

    /**
     * Test POST request: body sent with Content-Type, response converted, ETag extracted but not cached
     */
    @Test
    @DisplayName("POST should send body and extract ETag without caching")
    @ModuleDispatcher
    void postShouldSendBodyAndExtractETagWithoutCaching(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"id\":2,\"name\":\"created\"}", "\"etag-456\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .requestConverter(new StringRequestConverter())
                .build();

        // POST with body
        String requestBody = "{\"name\":\"new-item\"}";
        HttpResult<String> result = adapter.postBlocking(requestBody);

        assertTrue(result.isSuccess(), "POST should succeed");
        assertEquals("{\"id\":2,\"name\":\"created\"}", result.getContent().orElse(null));
        assertEquals("\"etag-456\"", result.getETag().orElse(null), "ETag should be extracted");

        // Verify request body was sent
        assertEquals(requestBody, dispatcher.getLastRequestBody().orElse(null));

        // Second POST should NOT use cache (POST is never cached)
        dispatcher.withSuccessAndETag("{\"id\":3,\"name\":\"another\"}", "\"etag-789\"");
        HttpResult<String> result2 = adapter.postBlocking(requestBody);

        assertTrue(result2.isSuccess());
        assertEquals("{\"id\":3,\"name\":\"another\"}", result2.getContent().orElse(null), "Should get fresh response, not cached");
        assertFalse(dispatcher.getLastIfNoneMatch().isPresent(), "POST should not send If-None-Match");
    }

    /**
     * Test PUT request: idempotent behavior, successful update
     */
    @Test
    @DisplayName("PUT should update resource idempotently")
    @ModuleDispatcher
    void putShouldUpdateResourceIdempotently(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"id\":1,\"name\":\"updated\"}", "\"etag-updated\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data", "1").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .requestConverter(new StringRequestConverter())
                .build();

        String updateBody = "{\"name\":\"updated\"}";
        HttpResult<String> result = adapter.putBlocking(updateBody);

        assertTrue(result.isSuccess(), "PUT should succeed");
        assertEquals("{\"id\":1,\"name\":\"updated\"}", result.getContent().orElse(null));
        assertEquals("\"etag-updated\"", result.getETag().orElse(null));
        assertEquals(updateBody, dispatcher.getLastRequestBody().orElse(null));
    }

    /**
     * Test DELETE request: no body sent, 204 response handled
     */
    @Test
    @DisplayName("DELETE should handle 204 No Content response")
    @ModuleDispatcher
    void deleteShouldHandle204NoContent(URIBuilder uriBuilder) {
        dispatcher.withNoContent();

        String serverUrl = uriBuilder.addPathSegments("api", "data", "1").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.deleteBlocking();

        assertTrue(result.isSuccess(), "DELETE should succeed");
        // 204 response has empty body, but StringResponseConverter converts empty string to empty Optional
        // Content may be present (empty string) or absent depending on converter implementation
        assertEquals(Optional.of(204), result.getHttpStatus());
    }

    /**
     * Test status-code-only adapter: DELETE with 204 must be reported as success
     * even though VoidResponseConverter never produces content.
     */
    @Test
    @DisplayName("statusCodeOnly DELETE should report 204 as success")
    @ModuleDispatcher
    void statusCodeOnlyDeleteShouldReport204AsSuccess(URIBuilder uriBuilder) {
        dispatcher.withNoContent();

        String serverUrl = uriBuilder.addPathSegments("api", "data", "1").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        HttpResult<Void> result = adapter.deleteBlocking();

        assertTrue(result.isSuccess(), "204 with Void converter must be success, not INVALID_CONTENT failure");
        assertEquals(Optional.of(204), result.getHttpStatus());
        assertTrue(result.getContent().isEmpty(), "Void result carries no content");
        assertTrue(result.getErrorCategory().isEmpty(), "Success must not carry an error category");
    }

    /**
     * Test status-code-only adapter: HEAD with 200 and ETag must be success with ETag exposed.
     */
    @Test
    @DisplayName("statusCodeOnly HEAD should report 200 as success and expose ETag")
    @ModuleDispatcher
    void statusCodeOnlyHeadShouldReport200AsSuccess(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("", "\"etag-head\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        HttpResult<Void> result = adapter.headBlocking();

        assertTrue(result.isSuccess(), "200 with Void converter must be success");
        assertEquals(Optional.of(200), result.getHttpStatus());
        assertEquals("\"etag-head\"", result.getETag().orElse(null), "ETag should be exposed");
    }

    /**
     * Test status-code-only adapter: GET with 200 and ETag must not fail on the
     * caching path even though the converter produces no cacheable content.
     */
    @Test
    @DisplayName("statusCodeOnly GET with ETag should succeed without caching")
    @ModuleDispatcher
    void statusCodeOnlyGetWithETagShouldSucceedWithoutCaching(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("ignored-body", "\"etag-get\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        HttpResult<Void> result = adapter.getBlocking();

        assertTrue(result.isSuccess(), "200 with Void converter must be success");
        assertEquals(Optional.of(200), result.getHttpStatus());

        // Second GET must not send If-None-Match (nothing was cached)
        HttpResult<Void> result2 = adapter.getBlocking();
        assertTrue(result2.isSuccess());
        assertFalse(dispatcher.getLastIfNoneMatch().isPresent(),
                "No If-None-Match expected - Void responses are never cached");
    }

    /**
     * GET should set the Accept header from the response converter's content type.
     */
    @Test
    @DisplayName("GET should send Accept header from response converter")
    @ModuleDispatcher
    void getShouldSendAcceptHeaderFromConverter(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{}", "\"e\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter()) // APPLICATION_JSON
                .build();

        adapter.getBlocking();

        assertEquals("application/json", dispatcher.getLastAccept().orElse(null),
                "Accept must be derived from the response converter's content type");
    }

    /**
     * POST should set Content-Type from the request converter, and a caller-provided
     * header must override the converter default.
     */
    @Test
    @DisplayName("POST should send Content-Type from request converter and honor caller override")
    @ModuleDispatcher
    void postShouldSendContentTypeAndHonorOverride(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{}", "\"e\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .requestConverter(new StringRequestConverter()) // APPLICATION_JSON
                .build();

        // Default from converter - Content-Type carries the charset (unlike Accept)
        adapter.postBlocking("{\"a\":1}");
        assertEquals("application/json; charset=UTF-8", dispatcher.getLastContentType().orElse(null));

        // Caller override wins
        adapter.post("{\"a\":1}", Map.of("Content-Type", "text/plain")).join();
        assertEquals("text/plain", dispatcher.getLastContentType().orElse(null),
                "Caller-provided Content-Type must override the converter default");
    }

    /**
     * Test server error: 503 response, returns SERVER_ERROR failure with status code
     */
    @Test
    @DisplayName("Server error (5xx) should return SERVER_ERROR failure")
    @ModuleDispatcher
    void serverErrorShouldReturnServerErrorFailure(URIBuilder uriBuilder) {
        dispatcher.withServerError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Server error should result in failure");
        assertEquals(HttpErrorCategory.SERVER_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(Optional.of(503), result.getHttpStatus());
        assertTrue(result.isRetryable(), "Server errors should be retryable");
    }

    /**
     * Test client error: 404 response, returns CLIENT_ERROR failure with status code
     */
    @Test
    @DisplayName("Client error (4xx) should return CLIENT_ERROR failure")
    @ModuleDispatcher
    void clientErrorShouldReturnClientErrorFailure(URIBuilder uriBuilder) {
        dispatcher.withClientError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Client error should result in failure");
        assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(Optional.of(404), result.getHttpStatus());
        assertFalse(result.isRetryable(), "Client errors should not be retryable");
    }

    /**
     * Test network failure: server not responding, connection refused is classified as NETWORK_ERROR or CONFIGURATION_ERROR
     * Note: Connection refused (port 1) may be classified as CONFIGURATION_ERROR since it's not a true IOException in flight.
     * True network errors (timeouts, connection drops) require MockWebServer simulation or are tested in retry scenarios.
     */
    @Test
    @DisplayName("Connection refused should return failure")
    void connectionRefusedShouldReturnFailure() {
        // Use an unreachable URL to simulate connection failure
        String unreachableUrl = "http://localhost:1/unreachable";
        HttpHandler handler = HttpHandler.builder().url(unreachableUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Connection failure should result in failure");
        // Connection refused can be NETWORK_ERROR or CONFIGURATION_ERROR depending on JDK implementation
        assertTrue(result.getErrorCategory().isPresent(), "Should have error category");
        assertTrue(result.getErrorMessage().isPresent(), "Should have error message");
    }

    // === Helper Converters ===

    private static class StringResponseConverter implements HttpResponseConverter<String> {
        @Override
        public Optional<String> convert(@Nullable Object rawContent) {
            return rawContent == null ? Optional.empty() : Optional.of(rawContent.toString());
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString();
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    /**
     * A stand-in for a real typed (JSON/XML) converter: it yields a value only for a payload it can
     * parse, and {@link Optional#empty()} for anything else — including an empty body.
     * <p>
     * {@link StringResponseConverter} maps an empty body to {@code Optional.of("")} and therefore
     * can never reach the adapter's conversion-failure branch, which is precisely why the
     * empty-content defects went unnoticed. Cases that need that branch use this converter instead.
     */
    private static class TypedResponseConverter implements HttpResponseConverter<String> {

        /** A non-empty body this converter cannot parse, used to drive the conversion-failure branch. */
        static final String UNPARSEABLE_BODY = "not-a-json-object";

        @Override
        public Optional<String> convert(@Nullable Object rawContent) {
            if (rawContent == null) {
                return Optional.empty();
            }
            String raw = rawContent.toString();
            return raw.startsWith("{") ? Optional.of(raw) : Optional.empty();
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString();
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    private static class StringRequestConverter implements HttpRequestConverter<String> {
        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(@Nullable String content) {
            return content == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }
}
