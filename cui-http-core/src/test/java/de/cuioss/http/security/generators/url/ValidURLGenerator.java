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
package de.cuioss.http.security.generators.url;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generates legitimate URL paths that the URL path pipeline accepts.
 *
 * <p>Every value is a path only: none carries a query string, because {@code ?} is not a path
 * character and a value with one is rejected by the path pipeline. Query parameters are the
 * subject of {@link ValidURLParameterGenerator} and {@link ValidURLParameterStringGenerator}.</p>
 *
 * <p>QI-6: Converted from fixedValues() to dynamic algorithmic generation.</p>
 */
public class ValidURLGenerator implements TypedGenerator<String> {

    // QI-6: Dynamic generation components
    private final TypedGenerator<Integer> pathTypeGen = Generators.integers(1, 7);
    private final TypedGenerator<Integer> idGen = Generators.integers(1, 999);
    private final TypedGenerator<Integer> pageGen = Generators.integers(1, 100);

    @Override
    public String next() {
        return switch (pathTypeGen.next()) {
            case 1 -> "/api/v1/users";
            case 2 -> "/static/css/style.css";
            case 3 -> "/index.html";
            case 4 -> "/docs/guide.pdf";
            case 5 -> "/search/results/page/" + pageGen.next();
            case 6 -> "/products/" + idGen.next() + "/reviews";
            case 7 -> "/admin/dashboard";
            default -> "/index.html";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}