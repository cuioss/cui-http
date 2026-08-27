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
package de.cuioss.http.security.generators.cookie;

import de.cuioss.http.security.data.Cookie;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ValidCookieGenerator}.
 *
 * <p>The defining property of this generator is legitimacy: the name and value are cookie tokens
 * carrying no attack marker of any kind, and the attributes are either absent or a well-formed
 * semicolon-separated list drawn from the seven attributes RFC 6265 defines. Asserting the
 * <em>absence</em> of traversal, null-byte, CRLF and control-character markers is what makes this
 * the counterpart of {@link AttackCookieGeneratorTest} rather than a shape check.</p>
 */
@EnableGeneratorController
@DisplayName("ValidCookieGenerator Contract Tests")
class ValidCookieGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** A legitimate cookie name and value stay inside the unreserved token alphabet. */
    private static final Pattern COOKIE_TOKEN = Pattern.compile("[A-Za-z0-9_]+");

    private static final String SECURE = "Secure";
    private static final String HTTP_ONLY = "HttpOnly";

    /** Attributes carrying no value; anything else must be a {@code Name=Value} pair. */
    private static final Set<String> FLAG_ATTRIBUTES = Set.of(SECURE, HTTP_ONLY);

    private static final String DOMAIN_PREFIX = "Domain=";
    private static final String PATH_PREFIX = "Path=";
    private static final String SAME_SITE_PREFIX = "SameSite=";
    private static final String MAX_AGE_PREFIX = "Max-Age=";
    private static final String EXPIRES_PREFIX = "Expires=";

    private static final List<String> VALUED_ATTRIBUTE_PREFIXES = List.of(
            DOMAIN_PREFIX, PATH_PREFIX, SAME_SITE_PREFIX, MAX_AGE_PREFIX, EXPIRES_PREFIX);

    /** The generator must emit at least this many distinct names to be worth calling a generator. */
    private static final int MINIMUM_DISTINCT_NAMES = 10;

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidCookieGenerator.class, count = 100)
    @DisplayName("Every generated cookie is a legitimate token pair with well-formed attributes")
    void shouldGenerateLegitimateCookie(Cookie generatedValue) {
        assertNotNull(generatedValue, "Generator must not produce null values");

        String name = generatedValue.name();
        String value = generatedValue.value();

        assertFalse(name.isEmpty(), "Cookie name should not be empty");
        assertTrue(COOKIE_TOKEN.matcher(name).matches(),
                () -> "Cookie name must be a token of [A-Za-z0-9_]. Name: <" + name + ">");
        assertTrue(COOKIE_TOKEN.matcher(value).matches(),
                () -> "Cookie value must be a token of [A-Za-z0-9_]. Value: <" + value + ">");

        assertCarriesNoAttackMarker(name, "Cookie name");
        assertCarriesNoAttackMarker(value, "Cookie value");

        assertWellFormedAttributes(generatedValue.attributes());
    }

    @Test
    @DisplayName("Should reach at least ten distinct names and both security flags")
    void shouldReachDistinctNamesAndSecurityFlags() {
        ValidCookieGenerator generator = new ValidCookieGenerator();
        Set<String> names = new HashSet<>();
        boolean secureSeen = false;
        boolean httpOnlySeen = false;

        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            Cookie cookie = generator.next();
            names.add(cookie.name());
            List<String> attributes = splitAttributes(cookie.attributes());
            secureSeen |= attributes.contains(SECURE);
            httpOnlySeen |= attributes.contains(HTTP_ONLY);
        }

        boolean secureReached = secureSeen;
        boolean httpOnlyReached = httpOnlySeen;
        assertAll("Generator variety across " + AGGREGATE_DRAWS + " draws",
                () -> assertTrue(names.size() >= MINIMUM_DISTINCT_NAMES,
                        () -> "Expected at least " + MINIMUM_DISTINCT_NAMES
                                + " distinct cookie names but got " + names.size() + ": " + names),
                () -> assertTrue(secureReached, "The Secure attribute must be reachable"),
                () -> assertTrue(httpOnlyReached, "The HttpOnly attribute must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(Cookie.class, new ValidCookieGenerator().getType(),
                "Generator should return Cookie.class");
    }

    private void assertCarriesNoAttackMarker(String component, String description) {
        assertAll(description + " must carry no attack marker: <" + component + ">",
                () -> assertContainsNone(component, TRAVERSAL_MARKERS, description + " (traversal)"),
                () -> assertContainsNone(component, NULL_BYTE_MARKERS, description + " (null-byte)"),
                () -> assertContainsNone(component, CRLF_MARKERS, description + " (CRLF)"),
                () -> assertFalse(containsControlCharacter(component),
                        () -> description + " must carry no control character: <" + component + ">"));
    }

    private void assertWellFormedAttributes(String attributes) {
        if (attributes.isEmpty()) {
            return;
        }
        for (String attribute : splitAttributes(attributes)) {
            assertTrue(isValidAttribute(attribute),
                    () -> "Attribute <" + attribute + "> is not a legitimate cookie attribute: it must be one of "
                            + FLAG_ATTRIBUTES + " / " + VALUED_ATTRIBUTE_PREFIXES
                            + ", carry a non-empty value, and — for Path and Max-Age — a value that is itself "
                            + "legitimate (no traversal segment, no negative age). Attributes: <" + attributes + ">");
        }
    }

    /**
     * Decides whether the attribute is one a legitimate cookie may carry. Membership in the
     * documented vocabulary is necessary but not sufficient: {@code Path} and {@code Max-Age} also
     * have to carry a legitimate <em>value</em>, otherwise this test would accept a hostile
     * {@code Path=../../../} or {@code Max-Age=-1} and stop being the counterpart of
     * {@link AttackCookieGeneratorTest}.
     */
    private boolean isValidAttribute(String attribute) {
        if (FLAG_ATTRIBUTES.contains(attribute)) {
            return true;
        }
        String prefix = VALUED_ATTRIBUTE_PREFIXES.stream()
                .filter(candidate -> attribute.startsWith(candidate) && attribute.length() > candidate.length())
                .findFirst()
                .orElse(null);
        if (prefix == null) {
            return false;
        }
        String attributeValue = attribute.substring(prefix.length());
        return switch (prefix) {
            case PATH_PREFIX -> carriesNoTraversalMarker(attributeValue);
            case MAX_AGE_PREFIX -> isNonNegativeAge(attributeValue);
            default -> true;
        };
    }

    private boolean carriesNoTraversalMarker(String pathValue) {
        return TRAVERSAL_MARKERS.stream().noneMatch(pathValue::contains);
    }

    /**
     * A legitimate {@code Max-Age} is a non-negative number of seconds; a negative value is the
     * delete-this-cookie instruction and anything unparsable is malformed.
     */
    private boolean isNonNegativeAge(String maxAgeValue) {
        try {
            return Long.parseLong(maxAgeValue) >= 0;
        } catch (NumberFormatException unparsable) {
            return false;
        }
    }

    /**
     * Splits on {@code ;} with a negative limit so trailing empty fields are preserved: a malformed
     * {@code "Secure;"} must surface as an empty attribute the validation above rejects, not be
     * silently dropped by {@code split}'s default trailing-empty removal.
     */
    private List<String> splitAttributes(String attributes) {
        if (attributes.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(attributes.split(";", -1)).map(String::trim).toList();
    }
}
