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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.dispatcher.TestContentDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link HttpHandler} using MockWebServer.
 * <p>
 * This test class focuses on HTTP integration scenarios:
 * <ul>
 *   <li>Ping operations (HEAD/GET) with various HTTP status codes</li>
 *   <li>Success (2xx), client error (4xx), and server error (5xx) handling</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("HttpHandler Integration Tests")
class HttpHandlerIntegrationTest {

    private final TestContentDispatcher dispatcher = new TestContentDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    @Test
    @DisplayName("pingHead should return success status for successful HTTP request")
    @ModuleDispatcher
    void pingHeadShouldReturnSuccessStatusForSuccessfulRequest(URIBuilder uriBuilder) {
        dispatcher.withSuccess("OK", null);

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder()
                .url(serverUrl)
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
                .build();

        HttpStatusFamily status = handler.pingGet();
        assertEquals(HttpStatusFamily.SERVER_ERROR, status,
                "ping should return SERVER_ERROR for 500 response");
    }
}
