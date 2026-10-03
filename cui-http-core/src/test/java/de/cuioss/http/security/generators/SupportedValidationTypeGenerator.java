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
package de.cuioss.http.security.generators;

import de.cuioss.http.security.core.ValidationType;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generator for supported ValidationType values in PipelineFactory.
 *
 * <p>QI-6: Converted from fixedValues() to dynamic algorithmic generation.</p>
 *
 * <p>Provides every ValidationType for which {@code PipelineFactory.createPipeline} builds a
 * pipeline: URL_PATH, PARAMETER_NAME, PARAMETER_VALUE, HEADER_NAME and HEADER_VALUE. The types
 * the factory rejects (COOKIE_NAME, COOKIE_VALUE, BODY) are never emitted.</p>
 */
public class SupportedValidationTypeGenerator implements TypedGenerator<ValidationType> {

    // QI-6: Dynamic generation components
    private final TypedGenerator<Integer> typeSelector = Generators.integers(1, 5);

    @Override
    public ValidationType next() {
        int selector = typeSelector.next();
        return switch (selector) {
            case 1 -> ValidationType.URL_PATH;
            case 2 -> ValidationType.PARAMETER_VALUE;
            case 3 -> ValidationType.HEADER_NAME;
            case 4 -> ValidationType.HEADER_VALUE;
            case 5 -> ValidationType.PARAMETER_NAME;
            default -> throw new IllegalStateException(
                    "Type selector out of range 1..5: " + selector);
        };
    }

    @Override
    public Class<ValidationType> getType() {
        return ValidationType.class;
    }
}