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
package de.cuioss.http.security.generators.header;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineAccepts;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract test for {@link ValidHTTPHeaderValueGenerator}.
 *
 * <p>The defining property of this generator is that every value is a well-formed value of one
 * of nine common header kinds - it has the grammar of exactly one of them - and that the
 * header-value pipeline accepts it. The aggregate test asserts that all nine kinds are
 * reachable.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("ValidHTTPHeaderValueGenerator Contract Tests")
class ValidHTTPHeaderValueGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    private static final String ENCODING = "(gzip|deflate|br|compress)";
    private static final String LANGUAGE = "(en-US|en|de|fr|es)";
    private static final String OPERATING_SYSTEM = "(Windows NT 6\\.2|Macintosh|X11; Linux|Android)";
    private static final String IP_ADDRESS = "(192\\.168\\.1\\.1|10\\.0\\.0\\.1|172\\.16\\.0\\.1|127\\.0\\.0\\.1)";

    private enum Kind {
        AUTHORIZATION("(Bearer|Basic) [A-Za-z]+"),
        CONTENT_TYPE("(application/json|text/html|application/xml|text/plain)"
                + "(; charset=(utf-8|iso-8859-1|us-ascii|utf-16))?"),
        ACCEPT_ENCODING(ENCODING + "(, " + ENCODING + "){0,2}"),
        ACCEPT_LANGUAGE(LANGUAGE + "(;q=[01]\\.\\d(," + LANGUAGE + ";q=[01]\\.\\d)?)?"),
        CACHE_CONTROL("(no-cache|max-age=3600|must-revalidate|private)(, max-age=\\d+)?"),
        USER_AGENT("Mozilla/5\\.0 \\(compatible; (MSIE 10\\.0|Chrome/91\\.0|Safari/537\\.36); " + OPERATING_SYSTEM
                + "\\)|(Chrome|Safari|Edge)/\\d+\\.0 \\(" + OPERATING_SYSTEM + "\\)"),
        CONNECTION("keep-alive|close|upgrade"),
        ORIGIN("https?://(example\\.com|app\\.example\\.org|localhost)(:\\d+)?"),
        FORWARDED_FOR(IP_ADDRESS + "(, " + IP_ADDRESS + ")?");

        private final Pattern grammar;

        Kind(String grammar) {
            this.grammar = Pattern.compile(grammar);
        }
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidHTTPHeaderValueGenerator.class, count = 200)
    @DisplayName("Every value has the grammar of exactly one header kind and is accepted by the header-value pipeline")
    void shouldGenerateWellFormedHeaderValue(String generatedValue) {
        kindOf(generatedValue);

        assertPipelineAccepts(
                new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter(),
                        ValidationType.HEADER_VALUE),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all nine documented header kinds")
    void shouldReachAllHeaderKinds() {
        ValidHTTPHeaderValueGenerator generator = new ValidHTTPHeaderValueGenerator();
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            kinds.add(kindOf(generator.next()));
        }

        assertEquals(EnumSet.allOf(Kind.class), kinds,
                "Every header kind must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ValidHTTPHeaderValueGenerator().getType(),
                "Generator should return String.class");
    }

    private static Kind kindOf(String value) {
        List<Kind> matching = EnumSet.allOf(Kind.class).stream()
                .filter(kind -> kind.grammar.matcher(value).matches())
                .toList();
        assertEquals(1, matching.size(),
                () -> "A value must have the grammar of exactly one header kind, but matched " + matching
                        + ". Value: <" + value + ">");
        return matching.getFirst();
    }
}
