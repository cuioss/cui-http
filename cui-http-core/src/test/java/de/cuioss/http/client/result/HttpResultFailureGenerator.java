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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generator for HttpResult.Failure instances with random error information.
 * Used in parameterized tests with @TypeGeneratorSource.
 */
public class HttpResultFailureGenerator implements TypedGenerator<HttpResult<String>> {

    private final TypedGenerator<String> strings = Generators.nonEmptyStrings();
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
}
