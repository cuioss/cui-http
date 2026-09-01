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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.dispatcher.RedirectDispatcher;
import de.cuioss.http.client.dispatcher.TestContentDispatcher;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link HttpHandler} using MockWebServer.
 * <p>
 * This test class focuses on HTTP integration scenarios:
 * <ul>
 *   <li>Ping operations (HEAD/GET) with various HTTP status codes</li>
 *   <li>Success (2xx), client error (4xx), and server error (5xx) handling</li>
 *   <li>That the JDK client never follows a redirect on either construction path</li>
 * </ul>
 * <p>
 * {@code HttpHandler} configures no JDK redirect policy, so the JDK default
 * {@link HttpClient.Redirect#NEVER} applies and the client itself never follows a hop. Redirect
 * following is done by cui-http, in {@link HttpHandler#send}, which revalidates every target against
 * the handler's {@link RedirectPolicy} before requesting it. That separation is asserted on two
 * levels here: at the policy level, by reading back {@link HttpClient#followRedirects()} on both the
 * HTTP-only and the HTTPS constructor path, and end-to-end against MockWebServer, by showing that a
 * caller taking the raw {@link HttpHandler#createHttpClient()} route still observes the 302
 * unfollowed and classifies it as the non-retryable {@link HttpErrorCategory#INVALID_CONTENT}.
 * <p>
 * The follow behaviour itself — which hops are taken, how they are rewritten, and which are refused —
 * is covered by {@code HttpHandlerRedirectTest}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("HttpHandler Integration Tests")
class HttpHandlerIntegrationTest {

    private final TestContentDispatcher dispatcher = new TestContentDispatcher();

    private final RedirectDispatcher redirectDispatcher = new RedirectDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    public ModuleDispatcherElement getRedirectDispatcher() {
        return redirectDispatcher;
    }

    @Test
    @DisplayName("pingHead should return success status for successful HTTP request")
    @ModuleDispatcher
    void pingHeadShouldReturnSuccessStatusForSuccessfulRequest(URIBuilder uriBuilder) {
        dispatcher.withSuccess("OK", null);

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder()
                .url(serverUrl)
                .allowInsecureHttp(true)
                .build();

        HttpStatusFamily status = handler.pingHead();
        assertEquals(HttpStatusFamily.SUCCESS, status,
                "pingHead should return SUCCESS for 200 response");
    }

    @Test
    @DisplayName("pingGet should return success status for successful HTTP request")
    @ModuleDispatcher
    void pingGetShouldReturnSuccessStatusForSuccessfulRequest(URIBuilder uriBuilder) {
        dispatcher.withSuccess("OK", null);

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder()
                .url(serverUrl)
                .allowInsecureHttp(true)
                .build();

        HttpStatusFamily status = handler.pingGet();
        assertEquals(HttpStatusFamily.SUCCESS, status,
                "pingGet should return SUCCESS for 200 response");
    }

    @Test
    @DisplayName("ping should handle different HTTP status codes correctly")
    @ModuleDispatcher
    void pingShouldHandleDifferentStatusCodesCorrectly(URIBuilder uriBuilder) {
        dispatcher.withClientError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder()
                .url(serverUrl)
                .allowInsecureHttp(true)
                .build();

        HttpStatusFamily status = handler.pingHead();
        assertEquals(HttpStatusFamily.CLIENT_ERROR, status,
                "ping should return CLIENT_ERROR for 404 response");
    }

    @Test
    @DisplayName("ping should handle server errors correctly")
    @ModuleDispatcher
    void pingShouldHandleServerErrorsCorrectly(URIBuilder uriBuilder) {
        dispatcher.withServerError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder()
                .url(serverUrl)
                .allowInsecureHttp(true)
                .build();

        HttpStatusFamily status = handler.pingGet();
        assertEquals(HttpStatusFamily.SERVER_ERROR, status,
                "ping should return SERVER_ERROR for 500 response");
    }

    @Test
    @DisplayName("The HTTP client path never follows a redirect itself")
    void httpClientPathDoesNotFollowRedirects() {
        HttpHandler handler = HttpHandler.builder()
                .url("http://example.com/api")
                .allowInsecureHttp(true)
                .build();

        assertEquals(HttpClient.Redirect.NEVER, handler.createHttpClient().followRedirects(),
                "the JDK client never follows a hop; cui-http does the following, revalidating each target first");
    }

    @Test
    @DisplayName("The HTTPS client path never follows a redirect itself")
    void httpsClientPathDoesNotFollowRedirects() {
        HttpHandler handler = HttpHandler.builder()
                .url("https://example.com/api")
                .build();

        assertEquals(HttpClient.Redirect.NEVER, handler.createHttpClient().followRedirects(),
                "the JDK client never follows a hop; cui-http does the following, revalidating each target first");
    }

    @Test
    @DisplayName("The raw client path surfaces a 302 unfollowed, with its Location header intact")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void redirectIsNotFollowedButSurfacesToCaller(URIBuilder uriBuilder) throws Exception {
        HttpResponse<String> response = get(uriBuilder, RedirectDispatcher.PATH_REDIRECT);

        // createHttpClient() hands out the raw JDK client, which bypasses the handler's follow loop:
        // a caller taking that route observes the 302 itself and is responsible for validating the
        // target. HttpHandler.send(...) is the route that follows; see HttpHandlerRedirectTest.
        assertEquals(302, response.statusCode(), "the raw client path must observe the redirect itself, not its target");
        assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_REDIRECT),
                "the response URI must still be the original request URI");
        assertTrue(response.previousResponse().isEmpty(), "the raw client path may walk no redirect hop");
        assertTrue(response.headers().firstValue("Location")
                        .orElse("").endsWith(RedirectDispatcher.PATH_TARGET),
                "the Location header must reach the caller so it can validate the target itself");
    }

    @Test
    @DisplayName("A 3xx the raw client did not follow classifies as non-retryable INVALID_CONTENT")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void unfollowedRedirectClassifiesAsInvalidContent(URIBuilder uriBuilder) throws Exception {
        HttpResponse<String> response = get(uriBuilder, RedirectDispatcher.PATH_REDIRECT);

        HttpStatusFamily family = HttpStatusFamily.fromStatusCode(response.statusCode());

        // Scope: the raw createHttpClient() route only. That client is Redirect.NEVER, so the 302
        // reaches the caller as an ordinary response carrying no usable representation. A hop the
        // RedirectPolicy refuses is a different path entirely — it never yields a 3xx response,
        // because HttpHandler.send(...) throws RedirectNotAllowedException instead; that refusal and
        // its CONFIGURATION_ERROR classification are asserted in RedirectPolicyTest, and the refusal
        // paths themselves in HttpHandlerRedirectTest.
        assertEquals(HttpStatusFamily.REDIRECTION, family, "a 302 must classify as REDIRECTION");
        assertEquals(HttpErrorCategory.INVALID_CONTENT, family.toErrorCategory(),
                "an unfollowed redirect carries no usable representation");
        assertFalse(family.toErrorCategory().isRetryable(),
                "retrying would only reproduce the same redirect");
    }

    private HttpResponse<String> get(URIBuilder uriBuilder, String path) throws Exception {
        URI target = URI.create(uriBuilder.build().toString().replaceAll("/$", "") + path);
        HttpHandler handler = HttpHandler.builder()
                .uri(target)
                .allowInsecureHttp(true)
                .build();
        return handler.createHttpClient().send(handler.requestBuilder().GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
