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

import java.util.Set;

import static de.cuioss.tools.collect.CollectionLiterals.immutableSet;

/**
 * Result states for HTTP operations with ETag-aware caching.
 * Extends the basic CUI result pattern with HTTP-specific semantics.
 *
 * <h2>States</h2>
 * <ul>
 *   <li>{@link #FRESH} - New content loaded from server</li>
 *   <li>{@link #CACHED} - Cached content validated as current (HTTP 304)</li>
 *   <li>{@link #STALE} - Cached content used due to error, may be outdated</li>
 *   <li>{@link #RECOVERED} - Operation succeeded after retry attempts</li>
 *   <li>{@link #ERROR} - All attempts failed, fallback content if available</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>1. ETag-Aware Caching</h3>
 * <pre>
 * HttpResultObject&lt;String&gt; result = etagHandler.load();
 * if (result.isFresh()) {
 *     // New content loaded, update cache
 *     updateCache(result.getResult(), result.getETag());
 * } else if (result.isCached()) {
 *     // Content unchanged, use existing cache
 *     useExistingContent(result.getResult());
 * }
 * </pre>
 *
 * <h3>2. Retry Flow Control</h3>
 * <pre>
 * if (result.isRecovered()) {
 *     // Succeeded after retries, log for monitoring
 *     logger.info("HTTP operation recovered after {} attempts",
 *         result.getRetryMetrics().getTotalAttempts());
 * } else if (result.getState() == ERROR) {
 *     // All retries failed, handle gracefully
 *     handleFailureWithFallback(result);
 * }
 * </pre>
 *
 * @author Oliver Wolff
 * @see HttpResultObject
 * @see de.cuioss.uimodel.result.ResultState
 * @since 1.0
 */
public enum HttpResultState {

    /**
     * Fresh content loaded from server.
     * Content is new or updated, ETag reflects current version.
     */
    FRESH,

    /**
     * Cached content validated as current.
     * Server returned HTTP 304 Not Modified, cached content remains valid.
     */
    CACHED,

    /**
     * Cached content used due to error.
     * Content may be outdated, indicates degraded operation.
     */
    STALE,

    /**
     * Operation succeeded after retry attempts.
     * Indicates temporary failures were overcome.
     */
    RECOVERED,

    /**
     * All retry attempts failed.
     * Fallback or default content may be available.
     */
    ERROR;

    /**
     * States using cached content: {@link #CACHED}, {@link #STALE}.
     */
    public static final Set<HttpResultState> CACHE_STATES = immutableSet(CACHED, STALE);

    /**
     * States indicating successful operations: {@link #FRESH}, {@link #CACHED}, {@link #RECOVERED}.
     */
    public static final Set<HttpResultState> SUCCESS_STATES = immutableSet(FRESH, CACHED, RECOVERED);

    /**
     * States indicating degraded operation: {@link #STALE}, {@link #RECOVERED}.
     */
    public static final Set<HttpResultState> DEGRADED_STATES = immutableSet(STALE, RECOVERED);

    /**
     * States requiring explicit handling: {@link #ERROR}, {@link #STALE}.
     */
    public static final Set<HttpResultState> MUST_BE_HANDLED = immutableSet(ERROR, STALE);
}