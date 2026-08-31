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
 *   <li>The {@link HttpClient.Redirect#NORMAL} redirect policy configured on both client paths</li>
 * </ul>
 * <p>
 * The HTTPS&#8594;HTTP downgrade that {@code NORMAL} refuses to follow is <strong>not</strong>
 * driven end-to-end here, because that needs a real TLS origin and an
 * {@code @EnableMockWebServer(useHttps = true)} server cannot accept a connection in this project
 * (see the class comment on {@code ResilientHttpAdapterIntegrationTest} for the
 * {@code NoSuchMethodError} root cause). The downgrade refusal is a property of
 * {@link HttpClient.Redirect#NORMAL} itself — it is precisely what separates {@code NORMAL} from
 * {@code ALWAYS} — so it is asserted at the policy level instead: both client paths are verified to
 * be configured with {@code NORMAL} rather than {@code ALWAYS} or the JDK's {@code NEVER} default.
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
    @DisplayName("The HTTP client path is configured with Redirect.NORMAL")
    void httpClientPathUsesRedirectNormal() {
        HttpHandler handler = HttpHandler.builder()
                .url("http://example.com/api")
                .allowInsecureHttp(true)
                .build();

        assertEquals(HttpClient.Redirect.NORMAL, handler.createHttpClient().followRedirects(),
                "the HTTP-only constructor must not leave the JDK's NEVER default in place");
    }

    @Test
    @DisplayName("The HTTPS client path is configured with Redirect.NORMAL")
    void httpsClientPathUsesRedirectNormal() {
        HttpHandler handler = HttpHandler.builder()
                .url("https://example.com/api")
                .build();

        assertEquals(HttpClient.Redirect.NORMAL, handler.createHttpClient().followRedirects(),
                "the HTTPS constructor must not leave the JDK's NEVER default in place");
    }

    @Test
    @DisplayName("A 302 to a same-server path is followed and the caller observes the final 200")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void singleHopRedirectIsFollowed(URIBuilder uriBuilder) throws Exception {
        HttpResponse<String> response = getFollowingRedirects(uriBuilder, RedirectDispatcher.PATH_REDIRECT);

        assertEquals(200, response.statusCode(), "the caller must observe the redirect target's status");
        assertEquals(RedirectDispatcher.TARGET_BODY, response.body(),
                "the caller must observe the redirect target's body");
        assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_TARGET),
                "the final response URI must be the redirect target, not the original request URI");
        assertEquals(1, redirectHops(response), "exactly one redirect hop must have been walked");
    }

    @Test
    @DisplayName("A two-hop redirect chain terminates at the target")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void redirectChainTerminatesAtTarget(URIBuilder uriBuilder) throws Exception {
        HttpResponse<String> response = getFollowingRedirects(uriBuilder, RedirectDispatcher.PATH_CHAIN_START);

        assertEquals(200, response.statusCode(), "the chain must resolve to the terminal 200");
        assertEquals(RedirectDispatcher.TARGET_BODY, response.body());
        assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_TARGET),
                "the chain must end at the target, not at an intermediate hop");
        assertEquals(2, redirectHops(response), "both hops of the chain must be walked");
    }

    /**
     * Counts the redirect responses the client walked to reach {@code response}, by unwinding the
     * {@link HttpResponse#previousResponse()} chain. This reads the client's own record of the
     * exchange rather than server-side counters, which the dispatcher resolver does not share with
     * the test instance.
     *
     * @param response the final response
     * @return the number of redirect hops that preceded it
     */
    private static int redirectHops(HttpResponse<String> response) {
        int hops = 0;
        for (HttpResponse<String> current = response;
                current.previousResponse().isPresent();
                current = current.previousResponse().get()) {
            hops++;
        }
        return hops;
    }

    private HttpResponse<String> getFollowingRedirects(URIBuilder uriBuilder, String path) throws Exception {
        URI target = URI.create(uriBuilder.build().toString().replaceAll("/$", "") + path);
        HttpHandler handler = HttpHandler.builder()
                .uri(target)
                .allowInsecureHttp(true)
                .build();
        return handler.createHttpClient().send(handler.requestBuilder().GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
