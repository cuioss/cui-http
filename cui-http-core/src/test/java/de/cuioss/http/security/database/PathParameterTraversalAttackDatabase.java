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
package de.cuioss.http.security.database;

import de.cuioss.http.security.core.UrlSecurityFailureType;

import java.util.List;

/**
 * Database of dot-segment path-parameter traversal patterns (the {@code ..;} family).
 *
 * <p><strong>Mechanic.</strong> RFC 3986 permits a {@code ;}-introduced path parameter on any path
 * segment, and every Servlet-style container strips those parameters before it resolves the path.
 * A segment spelled {@code ..;} — or {@code ..;foo=bar} — therefore reaches the resolver as a bare
 * {@code ..} and walks the parent directory, while the raw request line never contains the literal
 * {@code ../} that a naive traversal filter looks for. That gap between what the filter reads and
 * what the container resolves is the whole attack.</p>
 *
 * <p><strong>Why a dedicated database.</strong> This family has no single canonical CVE — it has
 * been reported repeatedly against different containers and reverse-proxy front ends over the
 * years — so it does not belong on a vendor-keyed CVE database such as
 * {@link ApacheCVEAttackDatabase}. Every entry declares
 * {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}; the surface that rejects it is
 * {@code NormalizationStage}'s LAYER 1 segment-anchored {@code ..;} pattern, which runs on the
 * decoded value before dot-segment resolution.</p>
 *
 * <p><strong>Seeding rule.</strong> The entries are exactly the payloads that were verified
 * <em>accepted</em> before the LAYER 1 pattern was added, so each one genuinely exercises the new
 * detection surface. {@code /..;/../etc/passwd} is deliberately excluded: it was already rejected
 * on its plain {@code ../} literal and would pass this database's tests without the {@code ..;}
 * mechanism ever being reached.</p>
 *
 * @since 1.0
 */
public class PathParameterTraversalAttackDatabase implements AttackDatabase {

    /** Two consecutive path-parameter dot-segments walking to the system password file. */
    public static final AttackTestCase DOUBLE_DOT_SEMICOLON_PASSWD = new AttackTestCase(
            "/..;/..;/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal: each '..;' segment carries an empty ';' path parameter that a Servlet-style container strips before path resolution, so the segment resolves to a bare '..' and walks two directories up toward the system password file, while the raw request line contains no literal '../' for a naive traversal filter to match.",
            "PATH_TRAVERSAL_DETECTED is expected because NormalizationStage's LAYER 1 segment-anchored '..;' pattern matches the dot-segment at a segment boundary before dot-segment resolution runs, rejecting the traversal intent regardless of how the path would later resolve."
    );

    /** The same family nested under a legitimate-looking API prefix. */
    public static final AttackTestCase API_PREFIXED_DOUBLE_DOT_SEMICOLON = new AttackTestCase(
            "/api/..;/..;/secret",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal hidden behind a legitimate '/api' prefix: the container strips the ';' path parameters, resolves both '..;' segments to '..', and escapes the API namespace to reach an adjacent 'secret' resource — a routing prefix offers no protection because the escape happens during path resolution, after routing has read the prefix.",
            "PATH_TRAVERSAL_DETECTED is expected because NormalizationStage's LAYER 1 segment-anchored '..;' pattern matches at the '/..;' segment boundary irrespective of the preceding prefix, so a legitimate leading segment does not mask the traversal intent."
    );

    /** Path parameters carrying real name=value pairs rather than an empty parameter. */
    public static final AttackTestCase NAMED_PATH_PARAMETERS_PASSWD = new AttackTestCase(
            "/..;foo=bar/..;baz=1/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal using populated 'name=value' path parameters instead of an empty ';': the container strips everything from the ';' onward, so '..;foo=bar' and '..;baz=1' both resolve to '..'. The parameter payload is arbitrary, which defeats any filter that tries to enumerate the exact suffix rather than anchoring on the leading '..'.",
            "PATH_TRAVERSAL_DETECTED is expected because NormalizationStage's LAYER 1 pattern anchors on the segment-leading '..' immediately followed by ';' and does not inspect the parameter payload, so an arbitrary 'name=value' suffix does not evade it."
    );

    /** Uppercase percent-encoded semicolon, revealed by the decoding stage. */
    public static final AttackTestCase ENCODED_SEMICOLON_UPPERCASE_PASSWD = new AttackTestCase(
            "/..%3B/..%3B/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal with the ';' percent-encoded as '%3B' (uppercase hex): a filter that matches on the raw request line sees no ';' at all, while the container decodes the escape, strips the resulting path parameter, and resolves each segment to '..'.",
            "PATH_TRAVERSAL_DETECTED is expected because DecodingStage resolves '%3B' to ';' before NormalizationStage runs, so the LAYER 1 segment-anchored '..;' pattern matches the decoded value."
    );

    /** Lowercase percent-encoded semicolon — hex digits are case-insensitive. */
    public static final AttackTestCase ENCODED_SEMICOLON_LOWERCASE_PASSWD = new AttackTestCase(
            "/..%3b/..%3b/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal with the ';' percent-encoded as '%3b' (lowercase hex). Percent-encoding hex digits are case-insensitive, so this denotes exactly the same byte as '%3B' and must be detected alike — case variation is a standard filter-evasion move.",
            "PATH_TRAVERSAL_DETECTED is expected because DecodingStage resolves '%3b' to ';' regardless of hex-digit case before NormalizationStage runs, so the LAYER 1 segment-anchored '..;' pattern matches the decoded value."
    );

    /** Both the dots and the semicolon encoded, so nothing distinctive survives in the raw form. */
    public static final AttackTestCase ENCODED_DOTS_AND_SEMICOLON_PASSWD = new AttackTestCase(
            "/%2e%2e;/%2e%2e;/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Dot-segment path-parameter traversal with the dots themselves percent-encoded as '%2e%2e' and the ';' left literal: the raw path contains neither '..' nor '../', so a raw-string traversal filter sees nothing, while the container decodes the dots, strips the path parameter, and resolves each segment to '..'.",
            "PATH_TRAVERSAL_DETECTED is expected because DecodingStage resolves '%2e%2e' to '..' before NormalizationStage runs, so the LAYER 1 segment-anchored '..;' pattern matches the decoded value."
    );

    /** The minimal form: a single path-parameter dot-segment at root. */
    public static final AttackTestCase MINIMAL_ROOT_DOT_SEMICOLON = new AttackTestCase(
            "/..;/",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "The minimal member of the family: a single '..;' segment at root with nothing following it. The container strips the ';' path parameter and resolves the segment to '..', so the probe reveals whether the front end normalizes path parameters at all — it is the shape an attacker sends first to fingerprint the gap before building a full traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because NormalizationStage's LAYER 1 segment-anchored '..;' pattern matches at the leading '/..;' boundary and the check runs before dot-segment resolution, so the traversal intent is rejected even though resolution alone would clamp this path harmlessly to root."
    );

    private static final List<AttackTestCase> ALL_ATTACK_TEST_CASES = List.of(
            DOUBLE_DOT_SEMICOLON_PASSWD,
            API_PREFIXED_DOUBLE_DOT_SEMICOLON,
            NAMED_PATH_PARAMETERS_PASSWD,
            ENCODED_SEMICOLON_UPPERCASE_PASSWD,
            ENCODED_SEMICOLON_LOWERCASE_PASSWD,
            ENCODED_DOTS_AND_SEMICOLON_PASSWD,
            MINIMAL_ROOT_DOT_SEMICOLON
    );

    @Override
    public Iterable<AttackTestCase> getAttackTestCases() {
        return ALL_ATTACK_TEST_CASES;
    }

    @Override
    public String getDatabaseName() {
        return "Path Parameter Traversal Attack Database";
    }

    @Override
    public String getDescription() {
        return "Dot-segment path-parameter traversal patterns (the '..;' family) in which a ';'-introduced path parameter hides a parent-directory segment from raw-string traversal filters, including percent-encoded spellings of the dots and of the semicolon";
    }

    /**
     * JUnit 5 ArgumentsProvider for parameterized testing without @MethodSource boilerplate.
     *
     * <pre>
     * &#64;ParameterizedTest
     * &#64;ArgumentsSource(PathParameterTraversalAttackDatabase.ArgumentsProvider.class)
     * void shouldRejectPathParameterTraversalAttacks(AttackTestCase testCase) {
     *     // Test implementation - NO static method or @MethodSource needed!
     * }
     * </pre>
     *
     * @since 1.0
     */
    public static class ArgumentsProvider extends AttackDatabase.ArgumentsProvider<PathParameterTraversalAttackDatabase> {
        // Implementation inherited - uses reflection to create database instance
    }
}
