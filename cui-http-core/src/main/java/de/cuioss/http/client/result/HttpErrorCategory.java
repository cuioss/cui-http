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

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Error categories for HTTP operations, used to classify failures for retry decisions.
 * Each category indicates whether the error is transient (retryable) or permanent.
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * HttpResult&lt;String&gt; result = httpAdapter.getBlocking();
 * if (!result.isSuccess()) {
 *     result.getErrorCategory().ifPresent(category -> {
 *         if (category.isRetryable()) {
 *             scheduleRetry();
 *         } else {
 *             useFallback();
 *         }
 *     });
 * }
 * </pre>
 *
 * @author Oliver Wolff
 * @see HttpResult
 * @since 1.0
 */
public enum HttpErrorCategory {

    // === Network / Server Errors (Retryable) ===

    /**
     * Network connectivity problems: timeouts, connection failures, DNS resolution failures.
     * Transient errors that may resolve with retry.
     */
    NETWORK_ERROR,

    /**
     * Server-side errors (HTTP 5xx responses).
     * Indicates remote server problems that may be transient.
     */
    SERVER_ERROR,

    // === Client / Content / Configuration Errors (Non-retryable) ===

    /**
     * Client-side errors (HTTP 4xx responses).
     * Indicates request problems requiring configuration or input changes.
     */
    CLIENT_ERROR,

    /**
     * Invalid or unparseable response content.
     * Includes empty responses, malformed JSON, invalid data structures.
     */
    INVALID_CONTENT,

    /**
     * Configuration or setup errors.
     * Includes invalid URLs, missing settings, SSL configuration issues, authentication failures.
     */
    CONFIGURATION_ERROR;

    /**
     * Returns whether this error category represents a transient condition worth retrying.
     * Only {@link #NETWORK_ERROR} and {@link #SERVER_ERROR} are retryable.
     *
     * @return true if error is retryable, false otherwise
     */
    public boolean isRetryable() {
        return this == NETWORK_ERROR || this == SERVER_ERROR;
    }

    /**
     * Classifies a {@link Throwable} raised while executing or building an HTTP request into an
     * error category. An {@link IOException} (connection/read timeout, DNS or connection failure)
     * maps to the retryable {@link #NETWORK_ERROR}; any other throwable (e.g.
     * {@link IllegalArgumentException} / {@link IllegalStateException} from request building, or a
     * misconfiguration) maps to the non-retryable {@link #CONFIGURATION_ERROR}.
     *
     * <p>Asynchronous pipelines ({@link java.util.concurrent.CompletableFuture}) deliver failures
     * wrapped in {@link CompletionException} / {@link ExecutionException}. Such wrappers are
     * unwrapped to their cause before classification, so an {@code IOException} surfaced through a
     * {@code CompletableFuture.exceptionally(...)} callback is still correctly classified as a
     * retryable network error.</p>
     *
     * <p>This is the single source of truth for exception classification. It deliberately uses a
     * JDK-only import so the {@code client.result} package does not depend on
     * {@code client.handler}, preserving the {@code handler &rarr; result} layering.</p>
     *
     * <h3>Usage</h3>
     * <pre>{@code
     * try {
     *     HttpResponse<String> response = httpClient.send(request, ofString());
     *     return HttpResult.success(response.body(), etag, response.statusCode());
     * } catch (IOException | InterruptedException e) {
     *     HttpErrorCategory category = HttpErrorCategory.fromException(e);
     *     return HttpResult.failure("Request failed", e, category);
     * }
     * }</pre>
     *
     * @param throwable the failure to classify (must not be {@code null})
     * @return the corresponding error category
     */
    public static HttpErrorCategory fromException(@NonNull Throwable throwable) {
        Throwable unwrapped = throwable;
        while ((unwrapped instanceof CompletionException || unwrapped instanceof ExecutionException)
                && unwrapped.getCause() != null && unwrapped.getCause() != unwrapped) {
            unwrapped = unwrapped.getCause();
        }
        return unwrapped instanceof IOException ? NETWORK_ERROR : CONFIGURATION_ERROR;
    }

}