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
 * Database of ModSecurity Core Rule Set (CRS) attack patterns focusing on HTTP protocol layer.
 *
 * <p><strong>CRITICAL MODSECURITY CRS DATABASE:</strong> This database contains attack patterns
 * from the OWASP ModSecurity Core Rule Set (CRS), the industry-standard WAF ruleset used by
 * millions of websites worldwide. These patterns represent actual attack signatures that
 * ModSecurity actively blocks in production environments.</p>
 *
 * <p>The Core Rule Set is continuously updated by security researchers and represents the
 * collective knowledge of web application firewall development over two decades. Each pattern
 * here is derived from actual CRS rules that detect and prevent HTTP protocol layer attacks.</p>
 *
 * <h3>HTTP Protocol Layer Focus</h3>
 * <p>This database strictly adheres to HTTP protocol layer validation concerns as per
 * architectural standards (excluding application-layer encodings like HTML entities,
 * JavaScript escapes, or Base64).</p>
 *
 * <h3>CRS Rule Categories Covered</h3>
 * <p>Every entry below is driven through {@code URLPathValidationPipeline}, so this list names
 * only categories that an entry in {@link #getAttackTestCases()} actually exercises at the URL-path
 * layer:</p>
 * <ul>
 *   <li><strong>Path Traversal</strong>: parent-reference traversal in its plain, percent-encoded,
 *       double-encoded, overlong-UTF-8 and mixed dot-segment spellings</li>
 *   <li><strong>Null Byte Injection</strong>: encoded {@code %00} used to truncate a path and
 *       bypass extension or prefix checks</li>
 *   <li><strong>Excessive Encoding Layers</strong>: multiply-encoded input used to evade
 *       single-decoding filters</li>
 *   <li><strong>CRLF Injection</strong>: encoded CR/LF in the request line, the response-splitting
 *       vector</li>
 *   <li><strong>Invalid Path Characters</strong>: characters that RFC 3986 does not permit in a
 *       URL path, such as the backslash</li>
 * </ul>
 *
 * <p><strong>Deliberately absent.</strong> CRS categories that have no URL-path expression are not
 * represented here, because this database is only ever driven through the URL-path pipeline:
 * request smuggling and chunked-transfer framing are message-framing attacks, header injection is
 * owned by the header pipeline, and session fixation is a cookie/session-management concern. No
 * entry claims them.</p>
 *
 * @since 1.0
 */
public class ModSecurityCRSAttackDatabase implements AttackDatabase {

    // CRS Rule 930100: Path Traversal Attack
    public static final AttackTestCase CRS_930100_PATH_TRAVERSAL = new AttackTestCase(
            "/../../../../../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 930100: Unix path traversal attack. This rule detects attempts to access files outside the web root using parent directory references, one of the most common attack vectors.",
            "PATH_TRAVERSAL_DETECTED is expected because this uses explicit parent directory sequences to escape the web root."
    );

    public static final AttackTestCase CRS_930100_WINDOWS_TRAVERSAL = new AttackTestCase(
            "/..\\..\\..\\..\\..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            UrlSecurityFailureType.INVALID_CHARACTER,
            "ModSecurity CRS 930100: Windows path traversal with backslashes. CRS detects both Unix and Windows path traversal patterns to provide cross-platform protection.",
            "INVALID_CHARACTER is expected because backslash characters are invalid in URL paths per RFC specifications."
    );

    // CRS Rule 930110: Path Traversal with Encoding
    public static final AttackTestCase CRS_930110_ENCODED_TRAVERSAL = new AttackTestCase(
            "/%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 930110: URL-encoded path traversal. This rule detects encoded directory traversal attempts that try to bypass basic string matching filters.",
            "PATH_TRAVERSAL_DETECTED is expected because URL-decoded content reveals directory traversal patterns."
    );

    public static final AttackTestCase CRS_930110_DOUBLE_ENCODED = new AttackTestCase(
            "/%252e%252e%252f%252e%252e%252fetc%252fpasswd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 930110: Double URL-encoded traversal. CRS detects multiple encoding layers used to bypass single-decoding security filters.",
            "PATH_TRAVERSAL_DETECTED is expected because double decoding reveals directory traversal attempts."
    );

    // CRS Rule 930120: OS File Access
    public static final AttackTestCase CRS_930120_PROC_ACCESS = new AttackTestCase(
            "/../proc/self/environ",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 930120: Linux /proc filesystem access via traversal. This rule blocks attempts to access process information through directory traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because this uses traversal to access sensitive system paths."
    );

    public static final AttackTestCase CRS_930120_WINDOWS_CONFIG = new AttackTestCase(
            "/../windows/system32/config/sam",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 930120: Windows system file access via traversal. CRS blocks access attempts to Windows system configuration files.",
            "PATH_TRAVERSAL_DETECTED is expected because this uses traversal to reference Windows system paths."
    );

    // CRS Rule 920100: Invalid HTTP Request Line
    public static final AttackTestCase CRS_920100_NULL_BYTE = new AttackTestCase(
            "/index.php%00.bak",
            UrlSecurityFailureType.NULL_BYTE_INJECTION,
            "ModSecurity CRS 920100: Null byte injection. This rule detects null bytes used to bypass file extension checks and access backup files.",
            "NULL_BYTE_INJECTION is expected because %00 represents null byte injection attempts."
    );

    public static final AttackTestCase CRS_920100_CRLF_INJECTION = new AttackTestCase(
            "/page%0d%0aSet-Cookie:evil=1",
            UrlSecurityFailureType.CONTROL_CHARACTERS,
            "ModSecurity CRS 920100: CRLF injection in the request line. The encoded CR/LF pair smuggles a Set-Cookie header into the response, the classic HTTP response-splitting vector.",
            "CONTROL_CHARACTERS is expected because %0d%0a survives the wire-form character check as valid percent-encoding and only reveals the carriage return once DecodingStage decodes it, where control characters are forbidden for URL paths."
    );

    // CRS Rule 920170: Multiple URL Encoding
    public static final AttackTestCase CRS_920170_TRIPLE_ENCODING = new AttackTestCase(
            "/%25252e%25252e%25252f%25252e%25252e%25252fetc",
            UrlSecurityFailureType.DOUBLE_ENCODING,
            "ModSecurity CRS 920170: Triple URL encoding detected. CRS identifies excessive encoding layers that indicate evasion attempts.",
            "DOUBLE_ENCODING is expected because multiple encoding layers (%25252e) indicate malicious evasion techniques."
    );

    // CRS Rule 920180: Invalid Range
    public static final AttackTestCase CRS_920180_OVERLONG_UTF8 = new AttackTestCase(
            "/%c0%ae%c0%ae/%c0%ae%c0%ae/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 920180: Overlong UTF-8 encoding. This rule detects invalid UTF-8 sequences that violate encoding specifications.",
            "PATH_TRAVERSAL_DETECTED is expected because overlong UTF-8 sequences (%c0%ae) decode to traversal patterns."
    );

    // CRS Rule 920240: Null byte truncating an administrative path
    public static final AttackTestCase CRS_920240_ADMIN_PATH_NULL_BYTE = new AttackTestCase(
            "/admin%00HTTP",
            UrlSecurityFailureType.NULL_BYTE_INJECTION,
            "ModSecurity CRS 920240: Encoded null byte appended to an administrative path. The trailing text after the null byte is intended to be discarded by a C-style consumer so that only '/admin' survives.",
            "NULL_BYTE_INJECTION is expected because CharacterValidationStage rejects the encoded %00 spelling in the wire form before any decoding occurs."
    );

    // CRS Rule 920440: URL File Extension Bypass
    public static final AttackTestCase CRS_920440_DOUBLE_EXTENSION = new AttackTestCase(
            "/download.php%00.pdf",
            UrlSecurityFailureType.NULL_BYTE_INJECTION,
            "ModSecurity CRS 920440: File extension bypass using null byte. CRS detects attempts to bypass file type restrictions through null byte injection.",
            "NULL_BYTE_INJECTION is expected because null bytes truncate strings to bypass extension checks."
    );

    // CRS Rule 921110: Traversal escaping an API endpoint prefix
    public static final AttackTestCase CRS_921110_API_PATH_TRAVERSAL = new AttackTestCase(
            "/api/../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 921110: Directory traversal escaping an API endpoint prefix. A legitimate-looking '/api' prefix is followed by parent references that climb out of the API namespace.",
            "PATH_TRAVERSAL_DETECTED is expected because the path contains literal '../' sequences that PatternMatchingStage matches on the raw wire form."
    );

    // CRS Rule 921150: Null byte truncating a redirect target
    public static final AttackTestCase CRS_921150_REDIRECT_NULL_BYTE = new AttackTestCase(
            "/redirect%00malicious",
            UrlSecurityFailureType.NULL_BYTE_INJECTION,
            "ModSecurity CRS 921150: Encoded null byte in a redirect path. The null byte is intended to truncate the redirect target so a consumer sees only '/redirect'.",
            "NULL_BYTE_INJECTION is expected because CharacterValidationStage rejects the encoded %00 spelling in the wire form before any decoding occurs."
    );

    // CRS Rule 931100: Traversal reaching a path segment literally named "file"
    public static final AttackTestCase CRS_931100_TRAVERSAL_TO_FILE_SEGMENT = new AttackTestCase(
            "/../../../file/etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 931100: Directory traversal reaching a path segment literally named 'file'. This is ordinary parent-reference traversal, not a protocol-handler (file://) inclusion attempt.",
            "PATH_TRAVERSAL_DETECTED is expected because the leading '../' sequences are matched on the raw wire form; the 'file' segment here is an ordinary path segment and contributes nothing to the verdict."
    );

    // CRS Rule 931110: PHP Wrapper Attack
    public static final AttackTestCase CRS_931110_PHP_WRAPPER = new AttackTestCase(
            "/../../../input/data",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 931110: Input manipulation via traversal. This rule blocks attempts to access input streams through traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because this uses directory traversal patterns."
    );

    // CRS Rule 931120: Windows UNC Path
    public static final AttackTestCase CRS_931120_UNC_PATH = new AttackTestCase(
            "/..//../server/share/file",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 931120: Network share access via traversal. CRS detects attempts to access network resources through traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because this uses traversal patterns to access network paths."
    );

    // CRS Rule 932100: Unix Command Injection (Path Context Only)
    public static final AttackTestCase CRS_932100_PATH_SEMICOLON = new AttackTestCase(
            "/admin;jsessionid=abc123/../../../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 932100: Traversal hidden behind a path parameter. The ';jsessionid=abc123' path parameter is a legitimate RFC 3986 path segment suffix, used here to disguise the traversal that follows it.",
            "PATH_TRAVERSAL_DETECTED is expected because the semicolon path parameter is an allowed path character that does not mask the '../' sequences, which PatternMatchingStage matches on the raw wire form."
    );

    // CRS Rule 933100: Path Normalization Attack
    public static final AttackTestCase CRS_933100_DOT_SEGMENT = new AttackTestCase(
            "/../././../etc/passwd",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 933100: Path normalization attack using mixed directory references. CRS detects excessive dot segments combined with traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because mixed dot segments with traversal indicate path manipulation."
    );

    // CRS Rule 941100: Session Fixation
    public static final AttackTestCase CRS_941100_DOTDOT_COOKIE = new AttackTestCase(
            "/../../../tmp/sess_123",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            "ModSecurity CRS 941100: Session file access via path traversal. This rule blocks attempts to access session files through directory traversal.",
            "PATH_TRAVERSAL_DETECTED is expected because this attempts to escape to temporary session directories."
    );

    private static final List<AttackTestCase> ALL_ATTACK_TEST_CASES = List.of(
            CRS_930100_PATH_TRAVERSAL,
            CRS_930100_WINDOWS_TRAVERSAL,
            CRS_930110_ENCODED_TRAVERSAL,
            CRS_930110_DOUBLE_ENCODED,
            CRS_930120_PROC_ACCESS,
            CRS_930120_WINDOWS_CONFIG,
            CRS_920100_NULL_BYTE,
            CRS_920100_CRLF_INJECTION,
            CRS_920170_TRIPLE_ENCODING,
            CRS_920180_OVERLONG_UTF8,
            CRS_920240_ADMIN_PATH_NULL_BYTE,
            CRS_920440_DOUBLE_EXTENSION,
            CRS_921110_API_PATH_TRAVERSAL,
            CRS_921150_REDIRECT_NULL_BYTE,
            CRS_931100_TRAVERSAL_TO_FILE_SEGMENT,
            CRS_931110_PHP_WRAPPER,
            CRS_931120_UNC_PATH,
            CRS_932100_PATH_SEMICOLON,
            CRS_933100_DOT_SEGMENT,
            CRS_941100_DOTDOT_COOKIE
    );

    @Override
    public Iterable<AttackTestCase> getAttackTestCases() {
        return ALL_ATTACK_TEST_CASES;
    }

    @Override
    public String getDatabaseName() {
        return "ModSecurity Core Rule Set Attack Database";
    }

    @Override
    public String getDescription() {
        return "Database of ModSecurity CRS patterns expressible at the URL-path layer: path traversal, null byte injection, excessive encoding layers, CRLF injection, and invalid path characters";
    }

    /**
     * Modern JUnit 5 ArgumentsProvider for seamless parameterized testing without @MethodSource boilerplate.
     *
     * <p><strong>Clean Usage Pattern (2024-2025):</strong></p>
     * <pre>
     * &#64;ParameterizedTest
     * &#64;ArgumentsSource(ModSecurityCRSAttackDatabase.ArgumentsProvider.class)
     * void shouldRejectCRSAttacks(AttackTestCase testCase) {
     *     // Test implementation - NO static method or @MethodSource needed!
     * }
     * </pre>
     *
     * @since 1.0
     */
    public static class ArgumentsProvider extends AttackDatabase.ArgumentsProvider<ModSecurityCRSAttackDatabase> {
        // Implementation inherited - uses reflection to create database instance
    }
}