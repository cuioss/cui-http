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
 * HTTP-specific result states that extend the basic CUI result pattern with semantics
 * tailored for HTTP operations, particularly ETag-aware caching and retry scenarios.
 *
 * <h2>State Overview</h2>
 * <ul>
 *   <li>{@link #FRESH} - Successfully loaded new content from server</li>
 *   <li>{@link #CACHED} - Using cached content (ETag indicates not modified)</li>
 *   <li>{@link #STALE} - Using cached content but it may be outdated</li>
 *   <li>{@link #RECOVERED} - Succeeded after retry attempts</li>
 *   <li>{@link #ERROR} - All attempts failed, using fallback if available</li>
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
 * <h2>State Semantics</h2>
 * <ul>
 *   <li><strong>FRESH</strong>: Content was loaded from server, ETag updated</li>
 *   <li><strong>CACHED</strong>: Server returned 304 Not Modified, content unchanged</li>
 *   <li><strong>STALE</strong>: Using cached content due to error, may be outdated</li>
 *   <li><strong>RECOVERED</strong>: Succeeded after 1+ retry attempts</li>
 *   <li><strong>ERROR</strong>: All retry attempts failed, using default/fallback result</li>
 * </ul>
 *
 * @author Implementation based on CUI result pattern
 * @see HttpResultObject
 * @see de.cuioss.uimodel.result.ResultState
 * @since 1.0
 */
public enum HttpResultState {

    /**
     * Successfully loaded fresh content from the server.
     * This indicates the content is new or has been updated since the last request.
     * ETag has been updated to reflect the current content version.
     */
    FRESH,

    /**
     * Using cached content because server indicated it hasn't changed.
     * This is the optimal case for ETag-aware operations where the server
     * returned HTTP 304 Not Modified, confirming cached content is current.
     */
    CACHED,

    /**
     * Using cached/fallback content because fresh content couldn't be retrieved.
     * The cached content may be outdated but provides graceful degradation.
     * This state indicates potential service degradation that should be monitored.
     */
    STALE,

    /**
     * Operation succeeded but only after retry attempts.
     * This indicates temporary network/server issues that were successfully overcome.
     * Retry metrics should be available for observability.
     */
    RECOVERED,

    /**
     * All retry attempts failed.
     * A default or cached result may be available for graceful degradation,
     * but the primary operation could not be completed successfully.
     */
    ERROR;

    /**
     * States that indicate successful content retrieval from cache.
     * These states mean the result can be used reliably for business logic.
     */
    public static final Set<HttpResultState> CACHE_STATES = immutableSet(CACHED, STALE);

    /**
     * States that indicate successful operation completion.
     * Content is available and can be used, though STALE and RECOVERED
     * may warrant logging for monitoring purposes.
     */
    public static final Set<HttpResultState> SUCCESS_STATES = immutableSet(FRESH, CACHED, RECOVERED);

    /**
     * States that indicate content freshness concerns.
     * These states suggest monitoring or validation may be appropriate.
     */
    public static final Set<HttpResultState> DEGRADED_STATES = immutableSet(STALE, RECOVERED);

    /**
     * States that must be handled before accessing the result.
     * Overrides the base ResultState behavior to include STALE state
     * as it may require special handling in some contexts.
     */
    public static final Set<HttpResultState> MUST_BE_HANDLED = immutableSet(ERROR, STALE);
}