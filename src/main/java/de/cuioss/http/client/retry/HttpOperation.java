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
package de.cuioss.http.client.retry;

import de.cuioss.http.client.result.HttpResultObject;

/**
 * Functional interface for HTTP operations that can be retried using the result pattern.
 *
 * <p>Operations return HttpResultObject which encapsulates both success and failure states,
 * eliminating the need for exception-based error handling in the retry infrastructure.</p>
 */
@FunctionalInterface
public interface HttpOperation<T> {

    /**
     * Executes the HTTP operation using the result pattern.
     *
     * @return HttpResultObject containing the operation result or error details
     */
    HttpResultObject<T> execute();
}