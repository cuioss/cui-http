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
package de.cuioss.http.client.result;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generator for {@link HttpResult} failure instances with random error information, optionally
 * carrying fallback content.
 * <p>
 * Instantiated directly as a test field and driven via {@link #next()}, for example:
 * <pre>{@code
 * private final HttpResultFailureGenerator failureGen = new HttpResultFailureGenerator();
 *
 * HttpResult<String> result = failureGen.next();
 * }</pre>
 */
public class HttpResultFailureGenerator implements TypedGenerator<HttpResult<String>> {

    private final TypedGenerator<String> strings = Generators.letterStrings(1, 64);
    private final TypedGenerator<HttpErrorCategory> categories = Generators.enumValues(HttpErrorCategory.class);
    private final TypedGenerator<Boolean> hasFallback = Generators.booleans();
    private final TypedGenerator<Integer> statusCodes = Generators.integers(400, 599);

    @Override
    public HttpResult<String> next() {
        String errorMessage = strings.next();
        HttpErrorCategory category = categories.next();

        if (hasFallback.next()) {
            return HttpResult.failureWithFallback(
                    errorMessage,
                    null,
                    strings.next(),
                    category,
                    strings.next(),
                    statusCodes.next());
        } else {
            return HttpResult.failure(errorMessage, null, category);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Generic type arguments are erased at runtime, so the only class token available is the raw
     * {@code HttpResult} one. The double cast re-attaches the {@code String} type argument the
     * declared return type carries; it is safe because {@link #next()} only ever produces
     * {@code HttpResult<String>} instances.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<HttpResult<String>> getType() {
        return (Class<HttpResult<String>>) (Class<?>) HttpResult.class;
    }
}
