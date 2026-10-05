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
 * Database of Internationalized Domain Name (IDN) attack patterns with comprehensive Unicode security coverage.
 *
 * <p><strong>CRITICAL UNICODE SECURITY DATABASE:</strong> This database contains IDN-specific
 * attack patterns that exploit Unicode homograph vulnerabilities, punycode encoding exploits,
 * and visual domain spoofing techniques. Each pattern uses exact Unicode character sequences
 * that are visually deceptive to humans but distinct to security systems.</p>
 *
 * <p>These attacks exploit the visual similarity between characters from different Unicode
 * scripts to deceive users into visiting malicious websites that appear legitimate. The attacks
 * combine domain spoofing with path traversal to create sophisticated multi-vector exploits.</p>
 *
 * <h3>Attack Categories</h3>
 * <ul>
 *   <li><strong>Homograph Attacks</strong> - Visually similar characters from different scripts</li>
 *   <li><strong>Punycode Exploitation</strong> - ASCII-compatible encoding bypass techniques</li>
 *   <li><strong>Mixed Script Attacks</strong> - Combining Latin, Cyrillic, and other scripts</li>
 *   <li><strong>Unicode Normalization</strong> - Composed vs decomposed character exploits</li>
 *   <li><strong>Bidirectional Text</strong> - Right-to-left override manipulation</li>
 *   <li><strong>Invisible Characters</strong> - Zero-width and control character insertion</li>
 * </ul>
 *
 * @since 1.0
 */
@SuppressWarnings("text:S6389")
public class IDNAttackDatabase implements AttackDatabase {

    // Homograph attacks - visually similar characters
    public static final AttackTestCase CYRILLIC_APPLE_HOMOGRAPH = new AttackTestCase(
            "http://\u0430pple.com/../../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Homograph attack using Cyrillic '\u0430' (U+0430) instead of Latin 'a' (U+0061) in apple.com domain. This attack exploits visual similarity between characters from different Unicode scripts to deceive users into visiting malicious websites that appear to be legitimate apple.com while performing path traversal.",
            "INVALID_CHARACTER is expected because the Cyrillic letter (U+0430) lies above code point 255, the upper bound of the extended range this database is validated with, so CharacterValidationStage rejects it first. The directory traversal sequences (../../../) that follow are never examined."
    );

    public static final AttackTestCase CYRILLIC_GOOGLE_HOMOGRAPH = new AttackTestCase(
            "http://g\u043E\u043Egle.com/admin/../../config",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Homograph attack using Cyrillic '\u043E' (U+043E) instead of Latin 'o' (U+006F) in google.com domain. This creates a visually identical domain that users cannot distinguish from the legitimate Google domain, combined with path traversal to access configuration files.",
            "INVALID_CHARACTER is expected due to the presence of Cyrillic characters in what appears to be a Latin domain name, indicating potential homograph spoofing combined with unauthorized directory access."
    );

    public static final AttackTestCase CYRILLIC_MICROSOFT_HOMOGRAPH = new AttackTestCase(
            "http://mi\u0441rosoft.com/../../etc/hosts",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Homograph attack using Cyrillic '\u0441' (U+0441) instead of Latin 'c' (U+0063) in microsoft.com domain. This attack targets users' trust in the Microsoft brand while attempting to access the system hosts file through path traversal.",
            "INVALID_CHARACTER is expected because the Cyrillic '\u0441' character creates a homograph spoofing attack against the Microsoft brand, combined with directory traversal patterns."
    );

    public static final AttackTestCase CYRILLIC_PAYPAL_HOMOGRAPH = new AttackTestCase(
            "http://\u0440\u0430ypal.com/../../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Advanced homograph attack using multiple Cyrillic characters: '\u0440' (U+0440) instead of Latin 'p' and '\u0430' (U+0430) instead of Latin 'a' in paypal.com. This targets financial services users with a highly deceptive domain combined with system file access attempts.",
            "INVALID_CHARACTER is expected due to multiple Cyrillic character substitutions creating a convincing paypal.com homograph, combined with path traversal to sensitive system files."
    );

    // Punycode exploitation
    public static final AttackTestCase PUNYCODE_APPLE_ATTACK = new AttackTestCase(
            "http://xn--pple-43d.com/../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Punycode exploitation attack where 'xn--pple-43d.com' is the ASCII-compatible encoding of '\u0430pple.com' (with Cyrillic '\u0430'). This bypasses ASCII-only domain filters while maintaining the visual deception, combined with path traversal to access system password files.",
            "PATH_TRAVERSAL_DETECTED is expected because punycode domains (xn--) are ASCII-compatible and pass character validation, so path traversal patterns are detected."
    );

    public static final AttackTestCase PUNYCODE_RUSSIAN_DOMAIN = new AttackTestCase(
            "http://xn--e1afmkfd.com/../../config",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Punycode attack using 'xn--e1afmkfd.com' which decodes to '\u043F\u0440\u0438\u043C\u0435\u0440.com' (Russian for 'example'). This demonstrates how punycode can be used to disguise non-Latin domains while performing path traversal attacks to access configuration files.",
            "PATH_TRAVERSAL_DETECTED is expected for punycode domains that are ASCII-compatible and pass character validation, allowing path traversal detection."
    );

    public static final AttackTestCase PUNYCODE_CHINESE_ATTACK = new AttackTestCase(
            "http://xn--fsq.com/admin/../../etc/hosts",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Punycode attack with 'xn--fsq.com' representing Chinese character '\u4F8B' (U+4F8B, meaning 'example'). This shows how single character domains can be used for attack obfuscation while accessing system files through path traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because punycode domains are ASCII-compatible and pass character validation, allowing path traversal detection."
    );

    // Mixed script attacks
    public static final AttackTestCase MIXED_SCRIPT_GOOGLE = new AttackTestCase(
            "http://goog1\u0435.com/../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Mixed script attack combining Latin characters, Arabic numeral '1', and Cyrillic '\u0435' (U+0435) in place of Latin 'e'. This creates a domain that closely resembles 'google.com' but contains characters from multiple Unicode scripts, making detection more difficult.",
            "INVALID_CHARACTER is expected because mixing scripts from different Unicode blocks (Latin + Cyrillic) in a single domain is a strong indicator of homograph spoofing attempts."
    );

    public static final AttackTestCase ONE_DOT_LEADER_TWITTER = new AttackTestCase(
            "http://twitter\u2024com/../../admin",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Sophisticated punctuation homograph attack using the one dot leader '\u2024' (U+2024) instead of period '.' (U+002E) in twitter.com domain. This creates a nearly impossible to detect visual deception while attempting administrative directory access.",
            "INVALID_CHARACTER is expected because non-ASCII punctuation characters in domain names, especially those visually similar to periods, indicate advanced homograph attacks."
    );

    public static final AttackTestCase HYPHENATION_POINT_INSTAGRAM = new AttackTestCase(
            "http://instagram\u2027com/../../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Advanced punctuation attack using hyphenation point '\u2027' (U+2027) instead of period in instagram.com. This character is visually identical to a period in most fonts, creating perfect visual spoofing while performing path traversal attacks.",
            "INVALID_CHARACTER is expected due to the use of Unicode punctuation that mimics ASCII period characters, combined with directory traversal to access sensitive system files."
    );

    // Unicode normalization bypass
    public static final AttackTestCase COMPOSED_ACCENT_CAFE = new AttackTestCase(
            "http://caf\u00E9.com/../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Unicode normalization attack using composed character '\u00E9' (U+00E9) in caf\u00E9.com. This tests whether security filters properly handle Unicode normalization forms, as this character could also be represented as 'e' + combining acute accent (U+0065 + U+0301).",
            "PATH_TRAVERSAL_DETECTED is expected because composed accent characters may pass character validation and allow path traversal detection."
    );

    public static final AttackTestCase DECOMPOSED_ACCENT_CAFE = new AttackTestCase(
            "http://cafe\u0301.com/../../config",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Unicode normalization attack using decomposed form: 'e' (U+0065) + a real combining acute accent code point (U+0301) instead of composed '\u00E9'. This tests normalization handling differences where the same visual appearance can be encoded in multiple ways, bypassing simple string matching filters.",
            "INVALID_CHARACTER is expected because the raw combining mark (U+0301) is a Unicode combining diacritical character, and combining marks are always rejected for URL paths (they enable homograph and normalization-bypass attacks) before any traversal detection runs."
    );

    // Right-to-left override attacks
    public static final AttackTestCase RTL_OVERRIDE_GOOGLE = new AttackTestCase(
            "http://evil\u202Emoc.elgoog.com/../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Right-to-left override attack using RTL override character (U+202E) to visually reverse text display. The domain appears as 'evil.google.com' to users but is actually 'evil\u202Emoc.elgoog.com', creating sophisticated visual deception combined with path traversal.",
            "INVALID_CHARACTER is expected because RTL override characters (U+202E) in domain names are strong indicators of bidirectional text spoofing attacks designed to deceive users about the true domain."
    );

    public static final AttackTestCase RTL_MIDDLE_GOOGLE = new AttackTestCase(
            "http://google\u202Eevil.com/../../etc/hosts",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "RTL override attack with the control character placed in the middle of the domain, making 'google\u202Eevil.com' appear as 'google.live.com' or similar to users depending on text rendering, while accessing system files through path traversal.",
            "INVALID_CHARACTER is expected due to the presence of RTL override control characters within domain names, indicating bidirectional text manipulation for spoofing purposes."
    );

    // Zero-width and invisible character attacks
    public static final AttackTestCase ZERO_WIDTH_SPACE_GOOGLE = new AttackTestCase(
            "http://goo\u200Bgle.com/../../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Zero-width space attack using invisible character '\u200B' (U+200B) inserted within google.com domain. This character is completely invisible to users but creates a different domain name that can bypass domain-based security filters while performing path traversal.",
            "INVALID_CHARACTER is expected because zero-width characters in domain names are indicators of steganographic attacks designed to hide malicious domains within legitimate-appearing URLs."
    );

    public static final AttackTestCase ZERO_WIDTH_NON_JOINER = new AttackTestCase(
            "http://micro\u200Csoft.com/../../config",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Zero-width non-joiner attack using invisible character '\u200C' (U+200C) within microsoft.com domain. This creates a domain that appears legitimate to users but is technically different, allowing bypass of domain whitelist security while attempting configuration file access.",
            "INVALID_CHARACTER is expected because zero-width non-joiner characters in domain names indicate attempts to create visually deceptive domains that bypass string-based security filters."
    );

    public static final AttackTestCase SOFT_HYPHEN_TWITTER = new AttackTestCase(
            "http://twit\u00ADter.com/../../../admin",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Soft hyphen attack using '\u00AD' (U+00AD) character within twitter.com domain. Soft hyphens are invisible unless needed for line breaking, creating domains that appear normal to users but are different to security systems, combined with administrative directory traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because soft hyphen characters may pass character validation and allow path traversal detection."
    );

    // Unicode confusables
    public static final AttackTestCase FULL_WIDTH_GOOGLE = new AttackTestCase(
            "http://\uFF47oogle.com/../../../etc/passwd",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Full-width character attack using full-width '\uFF47' (U+FF47) instead of regular Latin 'g' in google.com. Full-width characters are visually similar but technically different, commonly used in East Asian typography to create convincing domain spoofs.",
            "INVALID_CHARACTER is expected because full-width Unicode characters in domain names often indicate attempts to create visually similar domains for spoofing purposes."
    );

    public static final AttackTestCase MATHEMATICAL_BOLD_GOOGLE = new AttackTestCase(
            "http://\uD835\uDDF4\uD835\uDDFC\uD835\uDDFC\uD835\uDDF4\uD835\uDDF9\uD835\uDDF2.com/../../config",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Mathematical bold attack using mathematical bold characters (U+1D5F4-U+1D608) to create 'google' domain. These characters appear as bold text but are technically different Unicode code points, creating sophisticated homograph attacks that bypass simple character filtering.",
            "INVALID_CHARACTER is expected because mathematical Unicode characters in domain names are strong indicators of advanced homograph spoofing using specialized Unicode blocks."
    );

    public static final AttackTestCase MATHEMATICAL_ITALIC_GOOGLE = new AttackTestCase(
            "http://\uD835\uDC54\uD835\uDC5C\uD835\uDC5C\uD835\uDC54\uD835\uDC59\uD835\uDC52.com/admin/../etc/hosts",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Mathematical italic attack using mathematical italic characters (U+1D454-U+1D467) for 'google' domain. These create visually distinct but recognizable versions of Latin characters, used for sophisticated spoofing attacks against security-conscious users.",
            "INVALID_CHARACTER is expected because mathematical italic Unicode characters in domains indicate advanced homograph attacks using specialized mathematical character sets."
    );

    // Complex IDN with multiple vulnerabilities
    public static final AttackTestCase PUNYCODE_PORT_TRAVERSAL = new AttackTestCase(
            "http://xn--e1afmkfd.com:8080/../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "Complex multi-vector attack combining punycode domain (\u043F\u0440\u0438\u043C\u0435\u0440.com), non-standard port 8080, and path traversal. This demonstrates how IDN attacks can be combined with other techniques to create sophisticated multi-stage attacks targeting different security layers.",
            "PATH_TRAVERSAL_DETECTED is expected because punycode domains are ASCII-compatible and pass character validation, allowing path traversal detection."
    );

    public static final AttackTestCase MIXED_SCRIPT_HTTPS_TRAVERSAL = new AttackTestCase(
            "https://goog1\u0435.com/admin/../../config",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Advanced attack combining HTTPS protocol, mixed script homograph (Latin + Cyrillic), and administrative path traversal. This shows how attackers can use SSL encryption to add legitimacy to homograph attacks while performing unauthorized file access.",
            "INVALID_CHARACTER is expected because mixed script domains over HTTPS with administrative path traversal represent sophisticated social engineering combined with technical exploitation."
    );

    public static final AttackTestCase CYRILLIC_ZERO_WIDTH_TRAVERSAL = new AttackTestCase(
            "http://\u0430pp\u200Cle.com/../sensitive/data",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "Multi-layer attack combining Cyrillic homograph '\u0430' (U+0430), zero-width non-joiner '\u200C' (U+200C), and path traversal. This creates a domain that appears as 'apple.com' to users but contains multiple deceptive Unicode elements while accessing sensitive data.",
            "INVALID_CHARACTER is expected due to the combination of homograph characters and invisible Unicode characters in the domain, representing a multi-vector deception attack."
    );

    private static final List<AttackTestCase> ALL_ATTACK_TEST_CASES = List.of(
            CYRILLIC_APPLE_HOMOGRAPH,
            CYRILLIC_GOOGLE_HOMOGRAPH,
            CYRILLIC_MICROSOFT_HOMOGRAPH,
            CYRILLIC_PAYPAL_HOMOGRAPH,
            PUNYCODE_APPLE_ATTACK,
            PUNYCODE_RUSSIAN_DOMAIN,
            PUNYCODE_CHINESE_ATTACK,
            MIXED_SCRIPT_GOOGLE,
            ONE_DOT_LEADER_TWITTER,
            HYPHENATION_POINT_INSTAGRAM,
            COMPOSED_ACCENT_CAFE,
            DECOMPOSED_ACCENT_CAFE,
            RTL_OVERRIDE_GOOGLE,
            RTL_MIDDLE_GOOGLE,
            ZERO_WIDTH_SPACE_GOOGLE,
            ZERO_WIDTH_NON_JOINER,
            SOFT_HYPHEN_TWITTER,
            FULL_WIDTH_GOOGLE,
            MATHEMATICAL_BOLD_GOOGLE,
            MATHEMATICAL_ITALIC_GOOGLE,
            PUNYCODE_PORT_TRAVERSAL,
            MIXED_SCRIPT_HTTPS_TRAVERSAL,
            CYRILLIC_ZERO_WIDTH_TRAVERSAL
    );

    @Override
    public Iterable<AttackTestCase> getAttackTestCases() {
        return ALL_ATTACK_TEST_CASES;
    }

    @Override
    public String getDatabaseName() {
        return "IDN Attack Database";
    }

    @Override
    public String getDescription() {
        return "Comprehensive database of Internationalized Domain Name (IDN) attack patterns including homograph attacks, punycode exploitation, mixed scripts, Unicode normalization, bidirectional text manipulation, and invisible character insertion";
    }

    /**
     * Modern JUnit 5 ArgumentsProvider for seamless parameterized testing without @MethodSource boilerplate.
     *
     * <p><strong>Clean Usage Pattern (2024-2025):</strong></p>
     * <pre>
     * &#64;ParameterizedTest
     * &#64;ArgumentsSource(IDNAttackDatabase.ArgumentsProvider.class)
     * void shouldRejectIDNAttacks(AttackTestCase testCase) {
     *     // Test implementation - NO static method or @MethodSource needed!
     * }
     * </pre>
     *
     * @since 1.0
     */
    public static class ArgumentsProvider extends AttackDatabase.ArgumentsProvider<IDNAttackDatabase> {
        // Implementation inherited - uses reflection to create database instance
    }
}