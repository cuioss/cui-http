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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link AttackCookieGenerator}.
 *
 * <p>The defining property of this generator is that every emitted cookie is malicious in both
 * its value and its attributes: the value carries a payload from one of the eleven documented
 * attack families (or is a length attack in its own right), and the attributes carry a hostile
 * domain, a traversal path, an injected header, a negative max-age or a malformed form.</p>
 *
 * <p>The aggregate tests assert that all four name-attack families and all eleven value-attack
 * families are reachable, each identified by the literal payload its branch emits.</p>
 */
@EnableGeneratorController
@DisplayName("AttackCookieGenerator Contract Tests")
class AttackCookieGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** Below this length a value must carry a content marker; at or above it, length is the attack. */
    private static final int LENGTH_ATTACK_THRESHOLD = 5000;

    private static final Set<String> MALICIOUS_NAMES = Set.of(
            "", "   ", "cookie with spaces", "cookie=equals",
            "cookie;semicolon", "cookie,comma", "cookie[bracket]");

    private static final Set<String> SPECIAL_CHAR_NAMES = Set.of(
            "cookie{brace}", "cookie|pipe", "cookie\\backslash", "cookie\"quote", "cookie'apostrophe");

    private static final Set<String> CONTROL_CHAR_NAMES = Set.of(
            "cookie\ttab", "cookie\nnewline", "cookie\rcarriage");

    private static final String VERY_LONG_NAME_PREFIX = "very_long_cookie_name_";

    private static final String NAME_FAMILY_MALICIOUS = "malicious";
    private static final String NAME_FAMILY_SPECIAL_CHAR = "special-character";
    private static final String NAME_FAMILY_CONTROL_CHAR = "control-character";
    private static final String NAME_FAMILY_VERY_LONG = "very-long";

    private static final Set<String> ALL_NAME_FAMILIES = Set.of(
            NAME_FAMILY_MALICIOUS, NAME_FAMILY_SPECIAL_CHAR,
            NAME_FAMILY_CONTROL_CHAR, NAME_FAMILY_VERY_LONG);

    private static final String VALUE_FAMILY_XSS = "xss";
    private static final String VALUE_FAMILY_SQL = "sql-injection";
    private static final String VALUE_FAMILY_TRAVERSAL = "path-traversal";
    private static final String VALUE_FAMILY_NULL_BYTE = "null-byte";
    private static final String VALUE_FAMILY_JNDI = "jndi-lookup";
    private static final String VALUE_FAMILY_HEADER_INJECTION = "header-injection";
    private static final String VALUE_FAMILY_UNICODE = "bidi-override";
    private static final String VALUE_FAMILY_CONTROL_CHAR = "control-character";
    private static final String VALUE_FAMILY_VERY_LONG = "very-long";
    private static final String VALUE_FAMILY_JAVASCRIPT = "javascript-protocol";
    private static final String VALUE_FAMILY_DATA_URL = "data-url";

    private static final Set<String> ALL_VALUE_FAMILIES = Set.of(
            VALUE_FAMILY_XSS, VALUE_FAMILY_SQL, VALUE_FAMILY_TRAVERSAL, VALUE_FAMILY_NULL_BYTE,
            VALUE_FAMILY_JNDI, VALUE_FAMILY_HEADER_INJECTION, VALUE_FAMILY_UNICODE,
            VALUE_FAMILY_CONTROL_CHAR, VALUE_FAMILY_VERY_LONG, VALUE_FAMILY_JAVASCRIPT,
            VALUE_FAMILY_DATA_URL);

    private static final List<String> XSS_TAGS = List.of("<script>", "<img>", "<iframe>", "<object>");
    private static final List<String> SQL_COMMANDS = List.of("DROP TABLE", "DELETE FROM", "INSERT INTO");
    private static final String JNDI_PREFIX = "${jndi:ldap://";

    /**
     * U+202E RIGHT-TO-LEFT OVERRIDE, spelled as a code point rather than as a literal so the
     * marker cannot silently reorder the surrounding source text in an editor.
     */
    private static final String BIDI_OVERRIDE = Character.toString(0x202E);
    private static final String JAVASCRIPT_PREFIX = "javascript:";
    private static final String DATA_URL_PREFIX = "data:text/html,";

    /** The control-character value attack emits exactly these three forms. */
    private static final Set<String> CONTROL_CHAR_VALUES = Set.of(
            "\t injected", "\r injected", "\n injected");

    private static final List<String> HOSTILE_DOMAINS = List.of(
            ".evil.com", ".attacker.net", ".malicious.org");
    private static final String TRAVERSAL_PATH_ATTRIBUTE = "Path=../../../";
    private static final String NEGATIVE_MAX_AGE_ATTRIBUTE = "Max-Age=-1";

    /** The malformed-attribute branch emits exactly these four forms. */
    private static final Set<String> MALFORMED_ATTRIBUTES = Set.of(
            "Domain=; Path=", "Invalid=Attribute; Bad=Value", "Domain=", "=; Path=/");

    @ParameterizedTest
    @TypeGeneratorSource(value = AttackCookieGenerator.class, count = 100)
    @DisplayName("Every generated cookie carries a malicious value and malicious attributes")
    void shouldGenerateMaliciousCookie(Cookie generatedValue) {
        assertNotNull(generatedValue, "Generator must not produce null values");
        assertNotNull(generatedValue.name(), "Cookie name should not be null");

        assertFalse(valueFamiliesOf(generatedValue.value()).isEmpty(),
                () -> "Cookie value must carry an attack payload from one of the eleven documented "
                        + "families, or be a length attack of at least " + LENGTH_ATTACK_THRESHOLD
                        + " characters. Value: <" + preview(generatedValue.value()) + ">");

        assertTrue(isMaliciousAttributes(generatedValue.attributes()),
                () -> "Cookie attributes must carry a malicious marker. Attributes: <"
                        + generatedValue.attributes() + ">");
    }

    @Test
    @DisplayName("Should reach all four name-attack families")
    void shouldReachAllNameAttackFamilies() {
        assertEquals(ALL_NAME_FAMILIES, drawFamilies(cookie -> nameFamilyOf(cookie.name())),
                "Every name-attack family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should reach all eleven value-attack families")
    void shouldReachAllValueAttackFamilies() {
        AttackCookieGenerator generator = new AttackCookieGenerator();
        Set<String> reached = new HashSet<>();
        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            reached.addAll(valueFamiliesOf(generator.next().value()));
        }

        assertEquals(ALL_VALUE_FAMILIES, reached,
                "Every value-attack family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(Cookie.class, new AttackCookieGenerator().getType(),
                "Generator should return Cookie.class");
    }

    private Set<String> drawFamilies(Function<Cookie, String> classifier) {
        AttackCookieGenerator generator = new AttackCookieGenerator();
        Set<String> reached = new HashSet<>();
        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            reached.add(classifier.apply(generator.next()));
        }
        return reached;
    }

    private String nameFamilyOf(String name) {
        if (name.startsWith(VERY_LONG_NAME_PREFIX)) {
            return NAME_FAMILY_VERY_LONG;
        }
        if (CONTROL_CHAR_NAMES.contains(name)) {
            return NAME_FAMILY_CONTROL_CHAR;
        }
        if (SPECIAL_CHAR_NAMES.contains(name)) {
            return NAME_FAMILY_SPECIAL_CHAR;
        }
        assertTrue(MALICIOUS_NAMES.contains(name),
                () -> "Cookie name belongs to no documented name-attack family: <" + name + ">");
        return NAME_FAMILY_MALICIOUS;
    }

    /**
     * Returns every documented value-attack family the value matches. A value can legitimately
     * match more than one — the data-url branch embeds a script tag, and the header-injection
     * branch emits a raw CRLF — so the result is a set rather than a single verdict.
     */
    private Set<String> valueFamiliesOf(String value) {
        Set<String> families = new HashSet<>();
        if (XSS_TAGS.stream().anyMatch(value::contains)) {
            families.add(VALUE_FAMILY_XSS);
        }
        if (SQL_COMMANDS.stream().anyMatch(value::contains)) {
            families.add(VALUE_FAMILY_SQL);
        }
        if (TRAVERSAL_MARKERS.stream().anyMatch(value::contains)) {
            families.add(VALUE_FAMILY_TRAVERSAL);
        }
        if (NULL_BYTE_MARKERS.stream().anyMatch(value::contains)) {
            families.add(VALUE_FAMILY_NULL_BYTE);
        }
        if (value.contains(JNDI_PREFIX)) {
            families.add(VALUE_FAMILY_JNDI);
        }
        if (CRLF_MARKERS.stream().anyMatch(value::contains)) {
            families.add(VALUE_FAMILY_HEADER_INJECTION);
        }
        if (value.contains(BIDI_OVERRIDE)) {
            families.add(VALUE_FAMILY_UNICODE);
        }
        if (CONTROL_CHAR_VALUES.contains(value)) {
            families.add(VALUE_FAMILY_CONTROL_CHAR);
        }
        if (value.length() >= LENGTH_ATTACK_THRESHOLD) {
            families.add(VALUE_FAMILY_VERY_LONG);
        }
        if (value.startsWith(JAVASCRIPT_PREFIX)) {
            families.add(VALUE_FAMILY_JAVASCRIPT);
        }
        if (value.startsWith(DATA_URL_PREFIX)) {
            families.add(VALUE_FAMILY_DATA_URL);
        }
        return families;
    }

    private boolean isMaliciousAttributes(String attributes) {
        return HOSTILE_DOMAINS.stream().anyMatch(attributes::contains)
                || attributes.equals(TRAVERSAL_PATH_ATTRIBUTE)
                || CRLF_MARKERS.stream().anyMatch(attributes::contains)
                || NULL_BYTE_MARKERS.stream().anyMatch(attributes::contains)
                || attributes.equals(NEGATIVE_MAX_AGE_ATTRIBUTE)
                || MALFORMED_ATTRIBUTES.contains(attributes);
    }

    private String preview(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "...";
    }
}
