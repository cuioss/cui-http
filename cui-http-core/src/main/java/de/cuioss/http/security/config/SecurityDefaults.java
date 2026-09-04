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
package de.cuioss.http.security.config;

import java.util.Set;

/**
 * Collection of default values and constants for HTTP security configuration.
 *
 * <p>This class provides centralized constants for all security-related configuration values,
 * making it easy to reference standard limits, common patterns, and recommended settings
 * across the HTTP security validation system.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Centralized Constants</strong> - Single source of truth for all defaults</li>
 *   <li><strong>Security-First</strong> - Defaults prioritize security while maintaining usability</li>
 *   <li><strong>Industry Standards</strong> - Based on RFC specifications and best practices</li>
 *   <li><strong>Categorized</strong> - Organized by HTTP component type for easy navigation</li>
 * </ul>
 *
 * <h3>Constant Categories</h3>
 * <ul>
 *   <li><strong>Length Limits</strong> - Maximum sizes for various HTTP components</li>
 *   <li><strong>Count Limits</strong> - Maximum quantities for collections (advisory, see below)</li>
 *   <li><strong>Security Patterns</strong> - Common attack patterns to detect</li>
 *   <li><strong>Content Types</strong> - Standard MIME types and their security implications</li>
 *   <li><strong>Character Sets</strong> - Character validation patterns</li>
 *   <li><strong>Configuration Presets</strong> - Pre-built configurations for common scenarios</li>
 * </ul>
 *
 * <h3>Count and Classification Constants</h3>
 * <p>The count limits (parameter/header/cookie counts) are the preset defaults enforced by
 * {@code RequestCollectionValidator} (parameters 100, headers 50, cookies 20; strict 20/20/10,
 * lenient 500/100/50). The header/content-type classification sets
 * ({@code DANGEROUS_HEADER_NAMES}, {@code DANGEROUS_CONTENT_TYPES}, etc.) remain reference
 * values you may feed into the configurable allow/block lists.</p>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Use constants in configuration
 * SecurityConfiguration config = SecurityConfiguration.builder()
 *     .maxPathLength(SecurityDefaults.MAX_PATH_LENGTH_DEFAULT)
 *     .maxParameterValueLength(SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_DEFAULT)
 *     .build();
 *
 * // Use advisory constants for application-layer enforcement
 * if (request.getParameterMap().size() > SecurityDefaults.MAX_PARAMETER_COUNT_DEFAULT) {
 *     // reject request
 * }
 *
 * // Check against limits (UrlSecurityException is builder-constructed; its constructor is private)
 * if (path.length() > SecurityDefaults.MAX_PATH_LENGTH_STRICT) {
 *     throw UrlSecurityException.builder()
 *             .failureType(UrlSecurityFailureType.PATH_TOO_LONG)
 *             .validationType(ValidationType.URL_PATH)
 *             .originalInput(path)
 *             .build();
 * }
 *
 * // Use pattern constants
 * if (SecurityDefaults.PATH_TRAVERSAL_PATTERNS.stream().anyMatch(input::contains)) {
 *     // Handle path traversal attempt
 * }
 * </pre>
 *
 * @since 1.0
 * @see SecurityConfiguration
 * @see SecurityConfigurationBuilder
 */
public final class SecurityDefaults {

    /**
     * Private constructor to prevent instantiation.
     */
    private SecurityDefaults() {
        // Utility class - no instances
    }

    // ========== PATH SECURITY CONSTANTS ==========

    /** Maximum path length for strict security configurations */
    public static final int MAX_PATH_LENGTH_STRICT = 1024;

    /** Maximum path length for default security configurations */
    public static final int MAX_PATH_LENGTH_DEFAULT = 4096;

    /** Maximum path length for lenient security configurations */
    public static final int MAX_PATH_LENGTH_LENIENT = 8192;

    /** Common path traversal patterns to detect */
    public static final Set<String> PATH_TRAVERSAL_PATTERNS = Set.of(
            // Basic patterns
            "../", "..\\", "..\\/",

            // URL encoded patterns
            "..%2F", "..%5C", "%2E%2E/", "%2e%2e/", "%2E%2E%2F", "%2e%2e%2f",
            "%2e%2e%5c", "%2E%2E%5C", "%2f%2e%2e", "%5c%2e%2e",

            // Double encoded patterns
            "%252e%252e%252f", "%252e%252e%255c", "%252e%252e/", "%252e%252e\\",

            // Mixed patterns
            "....//", "....\\\\", ".%2E/", ".%2e/", "..//", "..\\\\",
            "%2e%2e//", "%2e%2e\\\\", "..%2f/", "..%5c\\", "..%2f", "..%5c", "/%2e%2e/",

            // UTF-8 overlong encodings (common bypass attempts)
            // %c0%af = overlong '/', %c1%9c = overlong '\', %c0%ae = overlong '.'
            "..%c0%af", "..%c1%9c", "%c0%ae%c0%ae%c0%af", "%c0%ae%c0%ae%c1%9c"
    );

    /**
     * URI scheme prefixes whose presence at the start of a URL path or parameter value is a
     * <em>structural</em> statement about the input: a path component may never begin with a
     * protocol handler. Enforced by {@code PatternMatchingStage} whenever
     * {@link SecurityConfiguration#failOnSuspiciousPatterns()} is enabled (as it is under
     * {@link SecurityConfiguration#strict()}).
     */
    public static final Set<String> PROTOCOL_HANDLER_SCHEMES = Set.of(
            "javascript:", "vbscript:", "data:", "file:"
    );

    /**
     * Filesystem paths and configuration filenames whose presence is an <em>application-layer
     * content judgement</em> rather than a structural defect: whether {@code /etc/} in a URL path
     * is an attack depends entirely on whether the application maps paths onto a filesystem.
     *
     * <p>These literals are therefore <strong>not</strong> enforced by any preset below
     * {@link SecurityConfiguration#paranoid()}. Feed this set into
     * {@link SecurityConfigurationBuilder#blockedPathPatterns(Set)} to reproduce the
     * {@code paranoid()} detection on any other base preset.</p>
     *
     * <p>The Windows entries keep their backslash delimiters, and that spelling is load-bearing in
     * two directions. They are <em>reachable</em> despite {@code CharacterValidationStage} rejecting
     * a raw backslash, because that stage validates the <em>wire</em> form — where {@code %5C} is a
     * well-formed escape — and {@code DecodingStage} then yields a literal backslash without
     * re-checking RFC 3986 character-set membership, so {@code /api/%5cwindows%5csystem32} arrives
     * here as {@code /api/\windows\system32}. And they are <em>safe</em> to match as substrings
     * precisely because a decoded backslash cannot occur in a legitimate URL path: respelling them
     * as bare segments would make {@code users} reject every {@code /users/...} REST route, which is
     * the false-positive class this tiering exists to remove.</p>
     */
    public static final Set<String> SENSITIVE_PATH_PATTERNS = Set.of(
            "/etc/", "/proc/", "/sys/", "/dev/", "/boot/", "/root/",
            "web.xml", "web.config", ".env", ".htaccess", ".htpasswd",
            "\\windows\\", "\\system32\\", "\\users\\", "\\program files\\"
    );

    // ========== PARAMETER SECURITY CONSTANTS ==========

    /** Maximum parameter count for strict security configurations */
    public static final int MAX_PARAMETER_COUNT_STRICT = 20;

    /** Maximum parameter count for default security configurations */
    public static final int MAX_PARAMETER_COUNT_DEFAULT = 100;

    /** Maximum parameter count for lenient security configurations */
    public static final int MAX_PARAMETER_COUNT_LENIENT = 500;

    /** Maximum parameter name length for strict configurations */
    public static final int MAX_PARAMETER_NAME_LENGTH_STRICT = 64;

    /** Maximum parameter name length for default configurations */
    public static final int MAX_PARAMETER_NAME_LENGTH_DEFAULT = 128;

    /** Maximum parameter name length for lenient configurations */
    public static final int MAX_PARAMETER_NAME_LENGTH_LENIENT = 256;

    /** Maximum parameter value length for strict configurations */
    public static final int MAX_PARAMETER_VALUE_LENGTH_STRICT = 1024;

    /** Maximum parameter value length for default configurations */
    public static final int MAX_PARAMETER_VALUE_LENGTH_DEFAULT = 2048;

    /** Maximum parameter value length for lenient configurations */
    public static final int MAX_PARAMETER_VALUE_LENGTH_LENIENT = 8192;

    /**
     * Parameter names that are commonly used in HTTP-layer attacks.
     *
     * <p>Like {@link #SENSITIVE_PATH_PATTERNS} this is an application-layer content judgement, so
     * it is enforced only where it seeds
     * {@link SecurityConfigurationBuilder#blockedParameterNames(Set)} - which
     * {@link SecurityConfiguration#paranoid()} does.</p>
     */
    public static final Set<String> SUSPICIOUS_PARAMETER_NAMES = Set.of(
            "script", "include", "require", "file", "path", "url", "redirect", "forward"
    );

    // ========== HEADER SECURITY CONSTANTS ==========

    /** Maximum header count for strict security configurations */
    public static final int MAX_HEADER_COUNT_STRICT = 20;

    /** Maximum header count for default security configurations */
    public static final int MAX_HEADER_COUNT_DEFAULT = 50;

    /** Maximum header count for lenient security configurations */
    public static final int MAX_HEADER_COUNT_LENIENT = 100;

    /** Maximum header name length for strict configurations */
    public static final int MAX_HEADER_NAME_LENGTH_STRICT = 64;

    /** Maximum header name length for default configurations */
    public static final int MAX_HEADER_NAME_LENGTH_DEFAULT = 128;

    /** Maximum header name length for lenient configurations */
    public static final int MAX_HEADER_NAME_LENGTH_LENIENT = 256;

    /** Maximum header value length for strict configurations */
    public static final int MAX_HEADER_VALUE_LENGTH_STRICT = 1024;

    /** Maximum header value length for default configurations */
    public static final int MAX_HEADER_VALUE_LENGTH_DEFAULT = 2048;

    /** Maximum header value length for lenient configurations */
    public static final int MAX_HEADER_VALUE_LENGTH_LENIENT = 8192;

    /** Headers that should typically be blocked for security */
    public static final Set<String> DANGEROUS_HEADER_NAMES = Set.of(
            "X-Debug", "X-Test", "X-Development", "X-Admin",
            "X-Execute", "X-Command", "X-Shell", "X-Eval",
            "Proxy-Authorization", "Proxy-Connection"
    );

    /** Headers commonly used for debugging that may expose sensitive information */
    public static final Set<String> DEBUG_HEADER_NAMES = Set.of(
            "X-Debug", "X-Trace", "X-Profile", "X-Test-Mode",
            "X-Development", "X-Internal", "X-System-Info"
    );

    // ========== COOKIE SECURITY CONSTANTS ==========

    /** Maximum cookie count for strict security configurations */
    public static final int MAX_COOKIE_COUNT_STRICT = 10;

    /** Maximum cookie count for default security configurations */
    public static final int MAX_COOKIE_COUNT_DEFAULT = 20;

    /** Maximum cookie count for lenient security configurations */
    public static final int MAX_COOKIE_COUNT_LENIENT = 50;

    /** Maximum cookie name length for strict configurations */
    public static final int MAX_COOKIE_NAME_LENGTH_STRICT = 64;

    /** Maximum cookie name length for default configurations */
    public static final int MAX_COOKIE_NAME_LENGTH_DEFAULT = 128;

    /** Maximum cookie name length for lenient configurations */
    public static final int MAX_COOKIE_NAME_LENGTH_LENIENT = 256;

    /** Maximum cookie value length for strict configurations */
    public static final int MAX_COOKIE_VALUE_LENGTH_STRICT = 1024;

    /** Maximum cookie value length for default configurations */
    public static final int MAX_COOKIE_VALUE_LENGTH_DEFAULT = 2048;

    /** Maximum cookie value length for lenient configurations */
    public static final int MAX_COOKIE_VALUE_LENGTH_LENIENT = 8192;

    /** Cookie names that may indicate security issues */
    public static final Set<String> SUSPICIOUS_COOKIE_NAMES = Set.of(
            "debug", "test", "admin", "root", "system", "internal",
            "password", "secret", "token", "key", "auth", "session"
    );

    // ========== BODY SECURITY CONSTANTS ==========

    /** Maximum body size for strict security configurations (1MB) */
    public static final long MAX_BODY_SIZE_STRICT = 1024L * 1024;

    /** Maximum body size for default security configurations (5MB) */
    public static final long MAX_BODY_SIZE_DEFAULT = 5L * 1024 * 1024;

    /** Maximum body size for lenient security configurations (10MB) */
    public static final long MAX_BODY_SIZE_LENIENT = 10L * 1024 * 1024;

    /** Content types that are generally safe for most applications */
    public static final Set<String> SAFE_CONTENT_TYPES = Set.of(
            "application/json", "application/xml", "text/plain", "text/html",
            "application/x-www-form-urlencoded", "multipart/form-data",
            "text/css", "text/javascript", "application/javascript"
    );

    /** Content types that may pose security risks */
    public static final Set<String> DANGEROUS_CONTENT_TYPES = Set.of(
            "application/octet-stream", "application/x-executable",
            "application/x-msdownload", "application/x-msdos-program",
            "application/x-java-archive", "application/java-archive",
            "text/x-script", "text/x-shellscript", "application/x-sh"
    );

    /** Content types used for file uploads */
    public static final Set<String> UPLOAD_CONTENT_TYPES = Set.of(
            "multipart/form-data", "application/octet-stream",
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/csv", "application/zip"
    );

    // ========== CHARACTER SECURITY CONSTANTS ==========

    /** Null byte character */
    public static final char NULL_BYTE = '\0';

    /** Common control characters that may be problematic */
    public static final Set<Character> PROBLEMATIC_CONTROL_CHARS = Set.of(
            '\0', '\1', '\2', '\3', '\4', '\5', '\6', '\7',
            '\b', '\f', '\016', '\017', '\020', '\021', '\022',
            '\023', '\024', '\025', '\026', '\027', '\030', '\031'
    );

    /** Characters commonly used in injection attacks */
    public static final Set<Character> INJECTION_CHARACTERS = Set.of(
            '<', '>', '\'', '"', '&', ';', '|', '`', '$', '(', ')', '{', '}'
    );


    // XSS patterns removed - application layer responsibility.
    // Application layers have proper context for HTML escaping and validation.

    // ========== ENCODING CONSTANTS ==========

    /** Common double-encoding patterns */
    public static final Set<String> DOUBLE_ENCODING_PATTERNS = Set.of(
            "%25", "%2525", "%252e", "%252f", "%255c",
            "%2e%2e", "%2f%2e%2e", "%5c%2e%2e"
    );

    /** Unicode normalization forms that should be checked */
    public static final Set<String> UNICODE_NORMALIZATION_FORMS = Set.of(
            "NFC", "NFD", "NFKC", "NFKD"
    );

    // ========== SIZE LIMITS FOR DIFFERENT SECURITY LEVELS ==========

    /**
     * Configuration preset for strict security requirements.
     *
     * <p>Single source of truth for strict preset semantics -
     * {@link SecurityConfiguration#strict()} delegates to this constant.</p>
     *
     * <p><strong>Why {@code caseSensitiveComparison} is {@code false} here.</strong>
     * Case-insensitive comparison lowercases both the input and the pattern set, so it matches a
     * superset of what case-sensitive comparison matches - enabling case sensitivity can only
     * reduce detection. Setting it {@code false} is therefore what makes the strict preset detect
     * at least as much as an equivalent hand-built configuration. Concretely,
     * {@link #PROTOCOL_HANDLER_SCHEMES}, {@link #SENSITIVE_PATH_PATTERNS} and
     * {@link #SUSPICIOUS_PARAMETER_NAMES} are all-lowercase literals, so under case-sensitive
     * comparison they cannot match a mixed-case input such as {@code JavaScript:alert(1)} or
     * {@code /ETC/passwd} at all. ({@link #PATH_TRAVERSAL_PATTERNS} is deliberately mixed-case and
     * is unaffected by that particular argument, but it too matches only the case permutations it
     * literally enumerates when comparison is case-sensitive.)</p>
     */
    public static final SecurityConfiguration STRICT_CONFIGURATION = new SecurityConfiguration(
            MAX_PATH_LENGTH_STRICT, false,
            MAX_PARAMETER_NAME_LENGTH_STRICT, MAX_PARAMETER_VALUE_LENGTH_STRICT,
            MAX_HEADER_NAME_LENGTH_STRICT, MAX_HEADER_VALUE_LENGTH_STRICT,
            MAX_COOKIE_NAME_LENGTH_STRICT, MAX_COOKIE_VALUE_LENGTH_STRICT,
            MAX_BODY_SIZE_STRICT,
            false, false, false, true, // no null bytes, no control chars, no extended ASCII, normalize Unicode
            false, true, // case-insensitive comparison (detects a superset), fail on suspicious patterns
            false, false, // requireSecureCookies, requireHttpOnlyCookies (opt-in)
            MAX_PARAMETER_COUNT_STRICT, MAX_HEADER_COUNT_STRICT, MAX_COOKIE_COUNT_STRICT,
            Set.of(), Set.of(), Set.of(), Set.of(), // allow/block lists (empty = allow-all, opt-in)
            Set.of(), Set.of()); // blockedPathPatterns, blockedParameterNames (see PARANOID_CONFIGURATION)

    /**
     * Configuration preset for balanced security and usability.
     *
     * <p>Single source of truth for default preset semantics -
     * {@link SecurityConfiguration#defaults()} delegates to this constant.
     * Identical to {@code SecurityConfiguration.builder().build()}.</p>
     */
    public static final SecurityConfiguration DEFAULT_CONFIGURATION = SecurityConfiguration.builder().build();

    /**
     * Configuration preset for maximum compatibility.
     *
     * <p>Single source of truth for lenient preset semantics -
     * {@link SecurityConfiguration#lenient()} delegates to this constant.
     * Even this preset never permits null bytes; path traversal is always
     * blocked by the validation stages regardless of configuration.</p>
     *
     * <p><strong>Security callout - this preset disables two independent detection
     * gates.</strong> A single {@link SecurityConfiguration#lenient()} selection turns
     * both of the following off together, so a caller choosing this preset gives up
     * both detections at once:</p>
     * <ul>
     *   <li>{@code allowDoubleEncoding = true} disables the double-encoding gate, so an
     *       input that hides an attack behind a second layer of percent-encoding (for
     *       example {@code %252e%252e%252f}) is no longer rejected on that basis.</li>
     *   <li>{@code normalizeUnicode = false} disables Unicode normalization, and with it
     *       the homoglyph/confusable detection that depends on normalization - so
     *       visually-identical characters from different scripts are no longer folded
     *       together before the input is compared against the pattern sets.</li>
     * </ul>
     *
     * <p>Choose this preset only where maximum compatibility genuinely outweighs both
     * of those detections; prefer {@link #DEFAULT_CONFIGURATION} when it does not.</p>
     */
    public static final SecurityConfiguration LENIENT_CONFIGURATION = new SecurityConfiguration(
            MAX_PATH_LENGTH_LENIENT, true,
            MAX_PARAMETER_NAME_LENGTH_LENIENT, MAX_PARAMETER_VALUE_LENGTH_LENIENT,
            MAX_HEADER_NAME_LENGTH_LENIENT, MAX_HEADER_VALUE_LENGTH_LENIENT,
            MAX_COOKIE_NAME_LENGTH_LENIENT, MAX_COOKIE_VALUE_LENGTH_LENIENT,
            MAX_BODY_SIZE_LENIENT,
            false, true, true, false, // no null bytes (never allowed), control chars, extended ASCII, no normalization
            false, false, // case-insensitive comparison, no suspicious-pattern failures
            false, false, // requireSecureCookies, requireHttpOnlyCookies (opt-in)
            MAX_PARAMETER_COUNT_LENIENT, MAX_HEADER_COUNT_LENIENT, MAX_COOKIE_COUNT_LENIENT,
            Set.of(), Set.of(), Set.of(), Set.of(), // allow/block lists (empty = allow-all, opt-in)
            Set.of(), Set.of()); // blockedPathPatterns, blockedParameterNames (see PARANOID_CONFIGURATION)

    /**
     * Configuration preset for applications that additionally want the application-layer content
     * detection this library keeps off by default.
     *
     * <p>Single source of truth for paranoid preset semantics -
     * {@link SecurityConfiguration#paranoid()} delegates to this constant.</p>
     *
     * <p>Identical to {@link #STRICT_CONFIGURATION} on every setting except that it seeds the two
     * content block-lists: {@code blockedPathPatterns} from {@link #SENSITIVE_PATH_PATTERNS} and
     * {@code blockedParameterNames} from {@link #SUSPICIOUS_PARAMETER_NAMES}. Because those lists
     * are orthogonal to the strictness predicate, {@code paranoid().isStrict()} is {@code true}.</p>
     *
     * <p><strong>False-positive profile.</strong> The seeded literals are filesystem paths and
     * configuration filenames, so this preset rejects any request whose path carries a matching
     * {@code /}-delimited segment ({@code /config/etc/settings}) or whose parameter name is
     * exactly one of {@code file}, {@code path}, {@code url} and their siblings - all of which are
     * legitimate vocabulary in many REST APIs. Choose it only where paths and parameters do reach
     * a filesystem; otherwise seed a narrower list of your own on another base preset.</p>
     *
     * <p>{@code caseSensitiveComparison} stays {@code false} here, per ADR-0012: a security preset
     * must never enable it, since case sensitivity can only reduce detection.</p>
     */
    public static final SecurityConfiguration PARANOID_CONFIGURATION = new SecurityConfiguration(
            MAX_PATH_LENGTH_STRICT, false,
            MAX_PARAMETER_NAME_LENGTH_STRICT, MAX_PARAMETER_VALUE_LENGTH_STRICT,
            MAX_HEADER_NAME_LENGTH_STRICT, MAX_HEADER_VALUE_LENGTH_STRICT,
            MAX_COOKIE_NAME_LENGTH_STRICT, MAX_COOKIE_VALUE_LENGTH_STRICT,
            MAX_BODY_SIZE_STRICT,
            false, false, false, true, // no null bytes, no control chars, no extended ASCII, normalize Unicode
            false, true, // case-insensitive comparison (detects a superset), fail on suspicious patterns
            false, false, // requireSecureCookies, requireHttpOnlyCookies (opt-in)
            MAX_PARAMETER_COUNT_STRICT, MAX_HEADER_COUNT_STRICT, MAX_COOKIE_COUNT_STRICT,
            Set.of(), Set.of(), Set.of(), Set.of(), // header/content-type allow/block lists (empty = allow-all)
            SENSITIVE_PATH_PATTERNS, SUSPICIOUS_PARAMETER_NAMES); // the application-layer content detection
}