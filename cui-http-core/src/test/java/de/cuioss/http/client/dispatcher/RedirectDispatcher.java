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
package de.cuioss.http.client.dispatcher;

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.Optional;
import java.util.Set;

/**
 * Test dispatcher serving a small redirect topology under {@value #BASE_PATH}, used to verify the
 * {@code Redirect.NORMAL} policy {@code HttpHandler} configures on its clients.
 * <p>
 * Routes:
 * <ul>
 *   <li>{@value #PATH_REDIRECT} — 302 to {@value #PATH_TARGET} (single hop)</li>
 *   <li>{@value #PATH_CHAIN_START} — 302 to {@value #PATH_CHAIN_MIDDLE}</li>
 *   <li>{@value #PATH_CHAIN_MIDDLE} — 302 to {@value #PATH_TARGET}</li>
 *   <li>{@value #PATH_TARGET} — 200 with body {@value #TARGET_BODY}</li>
 * </ul>
 * The dispatcher is stateless: tests assert that the hops were walked by unwinding the client's own
 * {@code HttpResponse.previousResponse()} chain, because the dispatcher resolver serves requests
 * from its own instance rather than the one held by the test.
 *
 * @author Oliver Wolff
 */
public class RedirectDispatcher implements ModuleDispatcherElement {

    /** Base path this dispatcher claims. */
    public static final String BASE_PATH = "/redirect";

    /** Single-hop redirect source. */
    public static final String PATH_REDIRECT = BASE_PATH + "/start";

    /** First hop of the two-hop chain. */
    public static final String PATH_CHAIN_START = BASE_PATH + "/chain-1";

    /** Second hop of the two-hop chain. */
    public static final String PATH_CHAIN_MIDDLE = BASE_PATH + "/chain-2";

    /** Terminal path that answers 200. */
    public static final String PATH_TARGET = BASE_PATH + "/target";

    /** Body served by {@value #PATH_TARGET}. */
    public static final String TARGET_BODY = "redirect-target-reached";

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        return handle(request);
    }

    @Override
    public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
        return handle(request);
    }

    private Optional<MockResponse> handle(RecordedRequest request) {
        String path = request.getUrl().encodedPath();
        return switch (path) {
            case PATH_REDIRECT, PATH_CHAIN_MIDDLE -> Optional.of(redirectTo(absolute(request, PATH_TARGET)));
            case PATH_CHAIN_START -> Optional.of(redirectTo(absolute(request, PATH_CHAIN_MIDDLE)));
            case PATH_TARGET -> Optional.of(new MockResponse(200,
                    new Headers.Builder().add("Content-Type", "text/plain").build(), TARGET_BODY));
            default -> Optional.empty();
        };
    }

    private static MockResponse redirectTo(String location) {
        return new MockResponse(302, new Headers.Builder()
                .add("Location", location)
                .add("Content-Type", "text/plain")
                .build(), "");
    }

    private static String absolute(RecordedRequest request, String path) {
        return request.getUrl().newBuilder().encodedPath(path).build().toString();
    }

    @Override
    public String getBaseUrl() {
        return BASE_PATH;
    }

    @Override
    public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD);
    }
}
