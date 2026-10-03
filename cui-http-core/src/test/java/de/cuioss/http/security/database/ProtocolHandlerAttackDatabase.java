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
 * Database of protocol handler attack patterns.
 *
 * <p>Every entry is a value that starts with one of the protocol handler schemes the library
 * enforces - {@code javascript:}, {@code vbscript:}, {@code data:} and {@code file:}
 * ({@code SecurityDefaults.PROTOCOL_HANDLER_SCHEMES}) - spelled raw, in another letter case, or
 * percent-encoded. The scheme is the <em>only</em> rejectable property of an entry: none carries a
 * traversal sequence, a character outside the path character set, a control character or a null
 * byte, so no other mechanism can decide it first.</p>
 *
 * <h3>Required configuration</h3>
 * <p>The scheme match rejects only when
 * {@code SecurityConfiguration.failOnSuspiciousPatterns()} is enabled, as it is under the
 * {@code strict()} and {@code paranoid()} presets. Under the default configuration the same
 * values are accepted by design, because whether a scheme is dangerous depends on the sink the
 * application passes the value to.</p>
 *
 * <h3>Protocol Attack Categories</h3>
 * <ul>
 *   <li><strong>JS Protocol Injection</strong> - Script execution through javascript: URLs</li>
 *   <li><strong>VBScript Protocol Injection</strong> - Script execution through vbscript: URLs</li>
 *   <li><strong>Data URI Exploitation</strong> - Malicious content via data: scheme</li>
 *   <li><strong>File Protocol Access</strong> - Local file system access via file: URLs</li>
 *   <li><strong>Case Manipulation</strong> - Scheme spelled in another letter case</li>
 *   <li><strong>Encoding Attacks</strong> - Percent-encoded scheme</li>
 * </ul>
 *
 * <h3>Not in this database</h3>
 * <p>Schemes outside the enforced set - {@code http:}, {@code https:}, {@code ftp:} and custom
 * schemes - are rejected by no preset, so a value carrying one of them is not a protocol handler
 * attack the library claims to detect and has no entry here.</p>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li><strong>RFC 3986</strong> - URI Generic Syntax specification</li>
 *   <li><strong>RFC 2397</strong> - data: URL scheme specification</li>
 *   <li><strong>CWE-79</strong> - Cross-site Scripting via protocol injection</li>
 *   <li><strong>CWE-88</strong> - Improper Neutralization of Script in Attributes</li>
 *   <li><strong>CWE-73</strong> - External Control of File Name or Path</li>
 * </ul>
 *
 * @since 1.0
 */
public class ProtocolHandlerAttackDatabase implements AttackDatabase {

    // JS Protocol Injection Attacks
    public static final AttackTestCase JAVASCRIPT_ALERT_BASIC = new AttackTestCase(
            "javascript:alert('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol injection using a basic alert() call. The javascript: URI scheme executes arbitrary script when the value reaches a navigation or href sink.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme, which the pattern stage matches at the start of the value before decoding."
    );

    public static final AttackTestCase JAVASCRIPT_ENCODED_EVAL = new AttackTestCase(
            "javascript:eval(String.fromCharCode(97,108,101,114,116,40,39,88,83,83,39,41))/admin",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol attack using eval() with String.fromCharCode() to obfuscate the script body. The character codes spell alert('XSS'), which defeats filters that look for the literal call.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme; the scheme match does not depend on the obfuscated script body."
    );

    public static final AttackTestCase JAVASCRIPT_LOCATION_REDIRECT = new AttackTestCase(
            "javascript:window.location='http://evil.com'",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol attack that assigns window.location to redirect the user to an attacker-controlled site.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme; the embedded http: URL is not at the start of the value and plays no part in the verdict."
    );

    public static final AttackTestCase JAVASCRIPT_FETCH_EXFILTRATION = new AttackTestCase(
            "javascript:fetch('/etc/passwd').then(console.log)",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol attack using the fetch() API to read a resource and hand the response to a callback for exfiltration.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme."
    );

    public static final AttackTestCase JAVASCRIPT_COMMENT_OBFUSCATION = new AttackTestCase(
            "javascript:/*comment*/alert('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol attack that places a script comment between the scheme and the payload to defeat filters matching 'javascript:alert'.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme; what follows the scheme is not inspected."
    );

    public static final AttackTestCase DOUBLE_JAVASCRIPT_PROTOCOL = new AttackTestCase(
            "javascript://javascript:alert('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "Nested JS protocol: the first javascript: is followed by a line comment that hides a second javascript: scheme from filters that strip one layer.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the javascript: scheme."
    );

    // VBScript Protocol Injection Attacks
    public static final AttackTestCase VBSCRIPT_MSGBOX = new AttackTestCase(
            "vbscript:msgbox('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "VBScript protocol injection using msgbox(). The vbscript: scheme executes script in legacy user agents the same way javascript: does.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the vbscript: scheme."
    );

    // Data URI Exploitation Attacks
    public static final AttackTestCase DATA_URI_HTML_SCRIPT = new AttackTestCase(
            "data:text/html,%3Cscript%3Ealert('XSS')%3C/script%3E",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "Data URI embedding an HTML document with a script element. The angle brackets are percent-encoded, as they are on the wire, so the value consists of path characters and escapes only.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the data: scheme, which the pattern stage matches before decoding."
    );

    public static final AttackTestCase DATA_URI_BASE64_SCRIPT = new AttackTestCase(
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4=",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "Base64-encoded data URI. The base64 text decodes to <script>alert('XSS')</script>, hiding the markup from filters that inspect the URL text.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the data: scheme; the base64 content is not decoded and plays no part in the verdict."
    );

    public static final AttackTestCase DATA_URI_SVG_SCRIPT = new AttackTestCase(
            "data:image/svg+xml,%3Csvg%3E%3Cscript%3Ealert('XSS')%3C/script%3E%3C/svg%3E",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "SVG data URI embedding a script element inside an SVG document, with the angle brackets percent-encoded.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the data: scheme."
    );

    // File Protocol Access Attacks
    public static final AttackTestCase FILE_PROTOCOL_UNIX_PASSWD = new AttackTestCase(
            "file:///etc/passwd",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "File protocol attack addressing the Unix password file directly through the file: scheme.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the file: scheme."
    );

    public static final AttackTestCase FILE_PROTOCOL_LOCALHOST = new AttackTestCase(
            "file://localhost/etc/shadow",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "File protocol attack with an explicit localhost authority addressing the shadow password file.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the file: scheme."
    );

    public static final AttackTestCase FILE_PROTOCOL_WINDOWS = new AttackTestCase(
            "file:///c:/windows/win.ini",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "File protocol attack addressing a Windows system file through a drive-letter path.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the value starts with the file: scheme."
    );

    // Case Manipulation Attacks
    public static final AttackTestCase MIXED_CASE_JAVASCRIPT = new AttackTestCase(
            "JavaScript:alert('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol with the scheme in mixed case. Scheme names are case-insensitive to a URL parser, so the casing changes nothing about what the value does.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the scheme match is case-insensitive under the default comparison: the value is lower-cased before it is tested against javascript:."
    );

    public static final AttackTestCase UPPER_CASE_DATA_URI = new AttackTestCase(
            "DATA:text/html,alert(1)",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "Data URI with the scheme in upper case.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the scheme match is case-insensitive under the default comparison: the value is lower-cased before it is tested against data:."
    );

    public static final AttackTestCase UPPER_CASE_FILE_PROTOCOL = new AttackTestCase(
            "FILE:///etc/passwd",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "File protocol with the scheme in upper case.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the scheme match is case-insensitive under the default comparison: the value is lower-cased before it is tested against file:."
    );

    // Protocol Encoding Attacks
    public static final AttackTestCase URL_ENCODED_JAVASCRIPT = new AttackTestCase(
            "%6a%61%76%61%73%63%72%69%70%74:alert('XSS')",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol with every letter of the scheme percent-encoded. '%6a%61%76%61%73%63%72%69%70%74' decodes to 'javascript', which defeats filters that look for the literal scheme without decoding.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the pattern stage runs a second time on the decoded value, where the value starts with the javascript: scheme."
    );

    public static final AttackTestCase PARTIALLY_ENCODED_JAVASCRIPT = new AttackTestCase(
            "j%61vascript:alert(1)",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol with a single letter of the scheme percent-encoded - the smallest change that breaks a literal match on 'javascript:'.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the pattern stage runs a second time on the decoded value, where the value starts with the javascript: scheme."
    );

    public static final AttackTestCase ENCODED_COLON_JAVASCRIPT = new AttackTestCase(
            "javascript%3Aalert(1)",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "JS protocol with the scheme delimiter percent-encoded: '%3A' decodes to ':', so the wire form carries no scheme at all.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the pattern stage runs a second time on the decoded value, where the value starts with the javascript: scheme."
    );

    public static final AttackTestCase URL_ENCODED_DATA_URI = new AttackTestCase(
            "%64%61%74%61:text/html,alert(1)",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "Data URI with the scheme percent-encoded. '%64%61%74%61' decodes to 'data'.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the pattern stage runs a second time on the decoded value, where the value starts with the data: scheme."
    );

    public static final AttackTestCase URL_ENCODED_FILE_PROTOCOL = new AttackTestCase(
            "%66%69%6c%65:///etc/passwd",
            UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED,
            "File protocol with the scheme percent-encoded. '%66%69%6c%65' decodes to 'file'.",
            "SUSPICIOUS_PATTERN_DETECTED is expected because the pattern stage runs a second time on the decoded value, where the value starts with the file: scheme."
    );

    private static final List<AttackTestCase> ALL_ATTACK_TEST_CASES = List.of(
            JAVASCRIPT_ALERT_BASIC,
            JAVASCRIPT_ENCODED_EVAL,
            JAVASCRIPT_LOCATION_REDIRECT,
            JAVASCRIPT_FETCH_EXFILTRATION,
            JAVASCRIPT_COMMENT_OBFUSCATION,
            DOUBLE_JAVASCRIPT_PROTOCOL,
            VBSCRIPT_MSGBOX,
            DATA_URI_HTML_SCRIPT,
            DATA_URI_BASE64_SCRIPT,
            DATA_URI_SVG_SCRIPT,
            FILE_PROTOCOL_UNIX_PASSWD,
            FILE_PROTOCOL_LOCALHOST,
            FILE_PROTOCOL_WINDOWS,
            MIXED_CASE_JAVASCRIPT,
            UPPER_CASE_DATA_URI,
            UPPER_CASE_FILE_PROTOCOL,
            URL_ENCODED_JAVASCRIPT,
            PARTIALLY_ENCODED_JAVASCRIPT,
            ENCODED_COLON_JAVASCRIPT,
            URL_ENCODED_DATA_URI,
            URL_ENCODED_FILE_PROTOCOL
    );

    @Override
    public Iterable<AttackTestCase> getAttackTestCases() {
        return ALL_ATTACK_TEST_CASES;
    }

    @Override
    public String getDatabaseName() {
        return "Protocol Handler Attack Database";
    }

    @Override
    public String getDescription() {
        return "Database of protocol handler attack patterns: javascript:, vbscript:, data: and file: schemes, spelled raw, in another letter case, or percent-encoded";
    }

    /**
     * Modern JUnit 5 ArgumentsProvider for seamless parameterized testing without @MethodSource boilerplate.
     *
     * <p><strong>Clean Usage Pattern (2024-2025):</strong></p>
     * <pre>
     * &#64;ParameterizedTest
     * &#64;ArgumentsSource(ProtocolHandlerAttackDatabase.ArgumentsProvider.class)
     * void shouldRejectProtocolHandlerAttacks(AttackTestCase testCase) {
     *     // Test implementation - NO static method or @MethodSource needed!
     * }
     * </pre>
     *
     * @since 1.0
     */
    public static class ArgumentsProvider extends AttackDatabase.ArgumentsProvider<ProtocolHandlerAttackDatabase> {
        // Implementation inherited - uses reflection to create database instance
    }
}
