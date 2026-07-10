/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Cookie prefix validation stage for RFC 6265bis cookie security prefixes.
 *
 * <p>This stage validates that cookies with security prefixes ({@code __Host-} and {@code __Secure-})
 * meet the requirements specified in RFC 6265bis. These prefixes provide additional security
 * guarantees to prevent subdomain attacks and ensure HTTPS-only transmission.</p>
 *
 * <p>In addition, {@link #validateCookie(Cookie)} enforces the opt-in configuration flags
 * {@code requireSecureCookies} and {@code requireHttpOnlyCookies} (both default {@code false}):
 * when enabled, every validated cookie must carry the {@code Secure} / {@code HttpOnly}
 * attribute respectively. These are meaningful only for attribute-bearing (Set-Cookie) cookies,
 * not request {@code Cookie}-header {@code name=value} pairs.</p>
 *
 * <p><strong>Standalone stage:</strong> unlike the URL/parameter/header stages, this stage is
 * <em>not</em> part of any pipeline built by {@code PipelineFactory} (which does not support
 * cookie validation types). It is invoked manually via {@link #validateCookie(de.cuioss.http.security.data.Cookie)}
 * on a {@link de.cuioss.http.security.data.Cookie} instance. The inherited
 * {@link #validate(String)} method only performs whitespace checks on the raw cookie name.</p>
 *
 * <h3>Validation Rules</h3>
 * <ol>
 *   <li><strong>__Host- Prefix</strong> - Requires:
 *     <ul>
 *       <li>Must have {@code Secure} attribute</li>
 *       <li>Must NOT have {@code Domain} attribute</li>
 *       <li>Must have {@code Path=/}</li>
 *     </ul>
 *   </li>
 *   <li><strong>__Secure- Prefix</strong> - Requires:
 *     <ul>
 *       <li>Must have {@code Secure} attribute</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>Security Value</h3>
 * <ul>
 *   <li><strong>Subdomain Protection</strong> - {@code __Host-} prevents cookie setting by subdomains</li>
 *   <li><strong>HTTPS Enforcement</strong> - Both prefixes ensure HTTPS-only transmission</li>
 *   <li><strong>Scope Control</strong> - {@code __Host-} restricts cookie scope to exact host and root path</li>
 *   <li><strong>Defense in Depth</strong> - Protects against cookie chaos attacks</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create prefix validator
 * CookiePrefixValidationStage validator = new CookiePrefixValidationStage();
 *
 * // Valid __Host- cookie
 * Cookie validHost = new Cookie("__Host-session", "abc123", "Secure; Path=/");
 * validator.validateCookie(validHost); // Passes
 *
 * // Invalid __Host- cookie (has Domain)
 * Cookie invalidHost = new Cookie("__Host-session", "abc123", "Domain=.example.com; Secure; Path=/");
 * try {
 *     validator.validateCookie(invalidHost); // Throws
 * } catch (UrlSecurityException e) {
 *     logger.warn("Invalid __Host- cookie: {}", e.getDetail());
 * }
 *
 * // Valid __Secure- cookie
 * Cookie validSecure = new Cookie("__Secure-token", "xyz789", "Secure; Domain=example.com");
 * validator.validateCookie(validSecure); // Passes
 *
 * // Invalid __Secure- cookie (missing Secure)
 * Cookie invalidSecure = new Cookie("__Secure-token", "xyz789", "Domain=example.com");
 * try {
 *     validator.validateCookie(invalidSecure); // Throws
 * } catch (UrlSecurityException e) {
 *     logger.warn("Invalid __Secure- cookie: {}", e.getDetail());
 * }
 * </pre>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>RFC Compliance</strong> - Implements RFC 6265bis prefix requirements</li>
 *   <li><strong>Fail-Secure</strong> - Throws exception on validation failure</li>
 *   <li><strong>Case-Sensitive</strong> - Cookie name prefixes are case-sensitive per RFC</li>
 *   <li><strong>Immutable</strong> - Thread-safe stateless validator</li>
 * </ul>
 *
 * <h3>Attack Prevention</h3>
 * <p>This validator prevents attacks documented in PortSwigger's "Cookie Chaos" research:</p>
 * <ul>
 *   <li>Unicode whitespace injection (prevented by character validation)</li>
 *   <li>Server-side normalization bypass (validates after normalization)</li>
 *   <li>Domain attribute injection for {@code __Host-} cookies</li>
 *   <li>Missing Secure attribute on prefix cookies</li>
 *   <li>Incorrect Path attribute on {@code __Host-} cookies</li>
 * </ul>
 *
 * @see Cookie
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis">RFC 6265bis</a>
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">Cookie Chaos Research</a>
 * @since 1.0
 */
public record CookiePrefixValidationStage(SecurityConfiguration config) implements HttpSecurityValidator {

    /**
     * Canonical constructor.
     *
     * @param config the security configuration driving optional attribute requirements
     *               ({@code requireSecureCookies} / {@code requireHttpOnlyCookies}); must not be null
     */
    public CookiePrefixValidationStage {
        Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Creates a stage using the default configuration, i.e. only RFC 6265bis prefix rules
     * (the {@code requireSecureCookies}/{@code requireHttpOnlyCookies} attribute requirements
     * default to off).
     */
    public CookiePrefixValidationStage() {
        this(SecurityConfiguration.defaults());
    }

    /** Prefix for host-locked cookies */
    private static final String HOST_PREFIX = "__Host-";

    /** Prefix for secure-only cookies */
    private static final String SECURE_PREFIX = "__Secure-";

    /**
     * Validates a cookie name against prefix requirements (string-based validation).
     *
     * <p>Note: This method only validates the cookie name format. For full prefix validation
     * including attribute requirements, use {@link #validateCookie(Cookie)} with a complete
     * Cookie object.</p>
     *
     * @param cookieName The cookie name to validate
     * @return The original cookie name wrapped in Optional if validation passes, Optional.empty() if input was null
     * @throws UrlSecurityException if the cookie name is invalid
     */
    @Override
    public Optional<String> validate(@Nullable String cookieName) throws UrlSecurityException {
        if (cookieName == null) {
            return Optional.empty();
        }

        // Check for leading/trailing whitespace
        if (!cookieName.equals(cookieName.trim())) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("Cookie name must not have leading or trailing whitespace")
                    .build();
        }

        // Validation passed
        return Optional.of(cookieName);
    }

    /**
     * Validates a complete cookie against prefix requirements.
     *
     * <p>This method performs comprehensive validation of cookie prefix requirements:</p>
     * <ul>
     *   <li>For {@code __Host-} prefix: validates Secure, no Domain, and Path=/</li>
     *   <li>For {@code __Secure-} prefix: validates Secure attribute</li>
     *   <li>For other cookies: validates name format (no leading/trailing whitespace)</li>
     * </ul>
     *
     * @param cookie The cookie to validate
     * @throws UrlSecurityException if the cookie violates prefix requirements
     */
    @SuppressWarnings({"java:S4449", "DataFlowIssue"}) // hasName() guarantees non-null after check
    public void validateCookie(Cookie cookie) throws UrlSecurityException {
        if (!cookie.hasName()) {
            // cookie.name() is null (or empty) here, so substitute an empty-string placeholder:
            // originalInput is @NonNull under @NullMarked and must never receive null.
            String safeName = cookie.name() == null ? "" : cookie.name();
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_INPUT)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(safeName)
                    .detail("Cookie must have a name")
                    .build();
        }

        String cookieName = cookie.name();

        // Validate name format (no leading/trailing whitespace)
        validate(cookieName);

        // Opt-in attribute requirements (default off). Meaningful for attribute-bearing
        // Set-Cookie cookies; a request Cookie-header name=value pair carries no attributes
        // and would always fail if these are enabled - enable them only for the Set-Cookie side.
        if (config.requireSecureCookies() && !cookie.isSecure()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("Cookie must have the Secure attribute (requireSecureCookies)")
                    .build();
        }
        if (config.requireHttpOnlyCookies() && !cookie.isHttpOnly()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("Cookie must have the HttpOnly attribute (requireHttpOnlyCookies)")
                    .build();
        }

        // Check for __Host- prefix requirements
        if (cookieName.startsWith(HOST_PREFIX)) {
            validateHostPrefix(cookie);
            return;
        }

        // Check for __Secure- prefix requirements
        if (cookieName.startsWith(SECURE_PREFIX)) {
            validateSecurePrefix(cookie);
        }

        // Other cookies don't need prefix validation
    }

    /**
     * Validates __Host- prefix requirements.
     *
     * @param cookie The cookie with __Host- prefix
     * @throws UrlSecurityException if requirements are not met
     */
    @SuppressWarnings({"java:S4449", "DataFlowIssue", "java:S3655"}) // Non-null: called after hasName() check in validateCookie
    private void validateHostPrefix(Cookie cookie) throws UrlSecurityException {

        String cookieName = cookie.name();

        // Requirement 1: Must have Secure attribute
        if (!cookie.isSecure()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("__Host- prefix requires Secure attribute")
                    .build();
        }

        // Requirement 2: Must NOT have Domain attribute
        if (cookie.getDomain().isPresent()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("__Host- prefix must not have Domain attribute (found: " +
                            cookie.getDomain().get() + ")")
                    .build();
        }

        // Requirement 3: Must have Path=/
        Optional<String> path = cookie.getPath();
        if (path.isEmpty() || !"/".equals(path.get())) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("__Host- prefix requires Path=/ (found: " +
                            path.orElse("none") + ")")
                    .build();
        }
    }

    /**
     * Validates __Secure- prefix requirements.
     *
     * @param cookie The cookie with __Secure- prefix
     * @throws UrlSecurityException if requirements are not met
     */
    @SuppressWarnings({"java:S4449", "DataFlowIssue"}) // Non-null: called after hasName() check in validateCookie
    private void validateSecurePrefix(Cookie cookie) throws UrlSecurityException {
        String cookieName = cookie.name();

        // Requirement: Must have Secure attribute
        if (!cookie.isSecure()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                    .validationType(ValidationType.COOKIE_NAME)
                    .originalInput(cookieName)
                    .detail("__Secure- prefix requires Secure attribute")
                    .build();
        }
    }

    /**
     * Checks if a cookie name has a security prefix.
     *
     * @param cookieName The cookie name to check
     * @return true if the name starts with __Host- or __Secure-
     */
    public static boolean hasSecurityPrefix(@Nullable String cookieName) {
        return cookieName != null &&
                (cookieName.startsWith(HOST_PREFIX) || cookieName.startsWith(SECURE_PREFIX));
    }

    /**
     * Checks if a cookie name has the __Host- prefix.
     *
     * @param cookieName The cookie name to check
     * @return true if the name starts with __Host-
     */
    public static boolean hasHostPrefix(@Nullable String cookieName) {
        return cookieName != null && cookieName.startsWith(HOST_PREFIX);
    }

    /**
     * Checks if a cookie name has the __Secure- prefix.
     *
     * @param cookieName The cookie name to check
     * @return true if the name starts with __Secure-
     */
    public static boolean hasSecurePrefix(@Nullable String cookieName) {
        return cookieName != null && cookieName.startsWith(SECURE_PREFIX);
    }
}
