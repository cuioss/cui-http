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
import java.util.regex.Pattern;

/**
 * Cookie prefix validation stage for RFC 6265bis cookie security prefixes.
 *
 * <p>This stage validates that cookies with security prefixes ({@code __Host-}, {@code __Secure-},
 * {@code __Http-} and {@code __HostHttp-}) meet the requirements specified in RFC 6265bis. These
 * prefixes provide additional security guarantees to prevent subdomain attacks, ensure HTTPS-only
 * transmission and keep the cookie out of reach of scripts.</p>
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
 * <p>The prefix token is matched ASCII case-insensitively (see the design principle below); the
 * four tokens do not overlap as string prefixes, so at most one of them ever applies.</p>
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
 *   <li><strong>__Http- Prefix</strong> - Requires:
 *     <ul>
 *       <li>Must have {@code Secure} attribute</li>
 *       <li>Must have {@code HttpOnly} attribute</li>
 *     </ul>
 *   </li>
 *   <li><strong>__HostHttp- Prefix</strong> - Requires:
 *     <ul>
 *       <li>Must have {@code Secure} attribute</li>
 *       <li>Must have {@code HttpOnly} attribute</li>
 *       <li>Must NOT have {@code Domain} attribute</li>
 *       <li>Must have {@code Path=/}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>Security Value</h3>
 * <ul>
 *   <li><strong>Subdomain Protection</strong> - {@code __Host-} and {@code __HostHttp-} prevent cookie setting by subdomains</li>
 *   <li><strong>HTTPS Enforcement</strong> - All four prefixes ensure HTTPS-only transmission</li>
 *   <li><strong>Script Isolation</strong> - {@code __Http-} and {@code __HostHttp-} keep the cookie out of {@code document.cookie}</li>
 *   <li><strong>Scope Control</strong> - {@code __Host-} and {@code __HostHttp-} restrict cookie scope to exact host and root path</li>
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
 *   <li><strong>Case-Insensitive</strong> - Cookie name prefixes are matched ASCII case-insensitively,
 *       as RFC 6265bis specifies; {@code __host-} and {@code __HOST-} carry the same requirements as
 *       {@code __Host-}, so a case variation cannot be used to slip past the prefix rules</li>
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
 *   <li>Case-variation of the prefix token (for example {@code __hOsT-}) to evade the prefix rules</li>
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

    /**
     * The RFC 6265bis cookie name prefixes and the attribute requirements each one carries.
     *
     * <p>Every prefix requires {@code Secure}, so that requirement is not modelled as a flag.
     * The four tokens do not overlap as string prefixes - {@code __HostHttp-} is not an extension
     * of {@code __Host-}, because the character following {@code __Host} differs - so at most one
     * constant ever matches a given name and the declaration order is not load-bearing.</p>
     */
    private enum SecurityPrefix {

        /** Host-locked cookie: Secure, no Domain, Path=/. */
        HOST("__Host-", false, true, true),

        /** Secure-only cookie: Secure. */
        SECURE("__Secure-", false, false, false),

        /** Script-inaccessible cookie: Secure and HttpOnly. */
        HTTP("__Http-", true, false, false),

        /** Host-locked, script-inaccessible cookie: Secure, HttpOnly, no Domain, Path=/. */
        HOST_HTTP("__HostHttp-", true, true, true);

        private final String token;
        private final boolean requiresHttpOnly;
        private final boolean forbidsDomain;
        private final boolean requiresRootPath;

        SecurityPrefix(String token, boolean requiresHttpOnly, boolean forbidsDomain, boolean requiresRootPath) {
            this.token = token;
            this.requiresHttpOnly = requiresHttpOnly;
            this.forbidsDomain = forbidsDomain;
            this.requiresRootPath = requiresRootPath;
        }

        /**
         * Finds the prefix a cookie name carries, matching the token ASCII case-insensitively.
         *
         * @param cookieName the cookie name to inspect; may be null
         * @return the matching prefix, or empty when the name carries none
         */
        static Optional<SecurityPrefix> match(@Nullable String cookieName) {
            if (cookieName == null) {
                return Optional.empty();
            }
            for (SecurityPrefix prefix : values()) {
                if (startsWithAsciiIgnoreCase(cookieName, prefix.token)) {
                    return Optional.of(prefix);
                }
            }
            return Optional.empty();
        }

        /**
         * ASCII-only case-insensitive prefix test.
         *
         * <p>Deliberately not {@link String#regionMatches(boolean, int, String, int, int)}: that
         * method folds case over the whole of Unicode, so a name such as {@code __ſecure-}
         * (LATIN SMALL LETTER LONG S) would be reported as prefixed although no user agent treats
         * it as such. RFC 6265bis specifies an ASCII case-insensitive comparison, and matching
         * exactly that keeps this stage's view of a name aligned with the user agent's.</p>
         *
         * @param value  the cookie name
         * @param prefix the prefix token, which contains ASCII characters only
         * @return true if {@code value} starts with {@code prefix}, ignoring ASCII case
         */
        private static boolean startsWithAsciiIgnoreCase(String value, String prefix) {
            if (value.length() < prefix.length()) {
                return false;
            }
            for (int i = 0; i < prefix.length(); i++) {
                if (toAsciiLower(value.charAt(i)) != toAsciiLower(prefix.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private static char toAsciiLower(char character) {
            return character >= 'A' && character <= 'Z' ? (char) (character + ('a' - 'A')) : character;
        }
    }

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
     * <p>Both components are character-validated first, against the RFC 6265 {@code cookie-name}
     * grammar and the {@code cookie-octet} set respectively, through
     * {@link CharacterValidationStage} under this stage's own {@link SecurityConfiguration}. That
     * check is what rejects a name carrying NBSP, ZWSP or IDEOGRAPHIC SPACE, none of which the
     * {@link #validate(String)} whitespace check can see, and a value carrying a semicolon, comma,
     * DQUOTE, backslash or any control character. CR/LF and every C0 control are rejected in both
     * components regardless of configuration.</p>
     *
     * <p>The prefix rules are then applied:</p>
     * <ul>
     *   <li>For {@code __Host-} prefix: validates Secure, no Domain, and Path=/</li>
     *   <li>For {@code __Secure-} prefix: validates Secure attribute</li>
     *   <li>For {@code __Http-} prefix: validates Secure and HttpOnly attributes</li>
     *   <li>For {@code __HostHttp-} prefix: validates Secure, HttpOnly, no Domain, and Path=/</li>
     *   <li>For other cookies: validates name format (no leading/trailing whitespace)</li>
     * </ul>
     *
     * <p>The prefix token is matched ASCII case-insensitively, so {@code __host-} carries the same
     * requirements as {@code __Host-}.</p>
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

        // Character validation for both components. The whitespace check above rests on
        // String.trim, which strips only code points <= U+0020 and therefore sees neither NBSP
        // (U+00A0) nor ZWSP (U+200B) nor IDEOGRAPHIC SPACE (U+3000); the character stage is what
        // catches those, along with every character outside the RFC 6265 cookie-name / cookie-octet
        // grammars. Both stages reject CR/LF and every C0 control unconditionally, so no separate
        // CR/LF branch is needed here.
        //
        // The stages are constructed per call rather than held as fields: this stage is a record,
        // and a record cannot declare an instance field derived from its component. Construction
        // reads three configuration booleans and a shared immutable character set, so it is cheap.
        new CharacterValidationStage(config, ValidationType.COOKIE_NAME).validate(cookieName);
        new CharacterValidationStage(config, ValidationType.COOKIE_VALUE).validate(cookie.value());

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

        // Cookies without a security prefix need no further validation.
        SecurityPrefix.match(cookieName).ifPresent(prefix -> validatePrefix(prefix, cookie, cookieName));
    }

    /**
     * Validates the attribute requirements the matched prefix carries.
     *
     * <p>The reported detail names the canonical prefix token rather than the casing the request
     * used, so a case variation cannot change the message a caller logs or matches on.</p>
     *
     * @param prefix     The prefix the cookie name carries
     * @param cookie     The cookie to validate
     * @param cookieName The cookie name, already established to be non-null by the caller
     * @throws UrlSecurityException if requirements are not met
     */
    private void validatePrefix(SecurityPrefix prefix, Cookie cookie, String cookieName) throws UrlSecurityException {

        // Every prefix requires the Secure attribute.
        if (!cookie.isSecure()) {
            throw prefixViolation(prefix, cookieName, "requires Secure attribute");
        }

        if (prefix.requiresHttpOnly && !cookie.isHttpOnly()) {
            throw prefixViolation(prefix, cookieName, "requires HttpOnly attribute");
        }

        Optional<String> domain = cookie.getDomain();
        if (prefix.forbidsDomain && domain.isPresent()) {
            throw prefixViolation(prefix, cookieName,
                    "must not have Domain attribute (found: " + renderForDetail(domain.get()) + ")");
        }

        if (prefix.requiresRootPath) {
            Optional<String> path = cookie.getPath();
            if (path.isEmpty() || !"/".equals(path.get())) {
                throw prefixViolation(prefix, cookieName,
                        "requires Path=/ (found: " + path.map(CookiePrefixValidationStage::renderForDetail)
                                .orElse("none") + ")");
            }
        }
    }

    /**
     * Maximum number of rendered characters {@link #renderForDetail(String)} emits.
     *
     * <p>Same limit and same reason as {@code AllowBlockListStage.MAX_RENDERED_DETAIL_LENGTH}, which
     * takes it in turn from {@code UrlSecurityException}; the value is duplicated rather than shared
     * because sharing would mean publishing an internal rendering limit on that exception's public
     * API for the sake of one collaborator in another package.</p>
     */
    private static final int MAX_RENDERED_DETAIL_LENGTH = 200;

    /** Marker appended when the rendered value was cut at {@link #MAX_RENDERED_DETAIL_LENGTH}. */
    private static final String TRUNCATION_MARKER = "...";

    /**
     * Code points {@link #renderForDetail(String)} escapes: the C0 controls (U+0000-U+001F), DEL
     * (U+007F), the C1 controls (U+0080-U+009F, notably NEL U+0085) and the Unicode line and
     * paragraph separators U+2028 and U+2029. Character-identical to the expression
     * {@code AllowBlockListStage} and {@code UrlSecurityException} use, and duplicated for the same
     * reason given on {@link #MAX_RENDERED_DETAIL_LENGTH}.
     */
    private static final Pattern CONTROL_CHARS_PATTERN =
            Pattern.compile("[\\x00-\\x1F\\x7F-\\u009F\\u2028\\u2029]");

    /**
     * Renders an attacker-controlled attribute value for inclusion in an exception detail.
     *
     * <p>The Domain and Path values reported above come straight off the request, and the detail is
     * included in {@code UrlSecurityException.getMessage()}, which callers log - so a raw CR or LF
     * spliced in would be log forging. Every control code point is therefore escaped to its
     * {@code U+XXXX} form. Escaping is expansive, so the result is additionally capped at
     * {@link #MAX_RENDERED_DETAIL_LENGTH} characters plus the {@link #TRUNCATION_MARKER}, and the
     * loop exits at the cut so the amplified string is never built. The cut is taken between
     * rendered code points, so a {@code U+XXXX} sequence is never split.</p>
     *
     * @param value the value to render
     * @return the value with every control code point escaped, bounded in length
     */
    private static String renderForDetail(String value) {
        StringBuilder rendered = new StringBuilder();
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            String literal = new String(Character.toChars(codePoint));
            String escaped = CONTROL_CHARS_PATTERN.matcher(literal).matches()
                    ? "U+%04X".formatted(codePoint)
                    : literal;
            if (rendered.length() + escaped.length() > MAX_RENDERED_DETAIL_LENGTH) {
                return rendered.append(TRUNCATION_MARKER).toString();
            }
            rendered.append(escaped);
        }
        return rendered.toString();
    }

    /**
     * Builds the prefix-violation exception, prefixing the detail with the canonical prefix token.
     *
     * @param prefix     The prefix whose rule was violated
     * @param cookieName The offending cookie name
     * @param violation  The violated requirement, phrased to follow "{@code <token> prefix }"
     * @return the exception to throw
     */
    private static UrlSecurityException prefixViolation(SecurityPrefix prefix, String cookieName, String violation) {
        return UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION)
                .validationType(ValidationType.COOKIE_NAME)
                .originalInput(cookieName)
                .detail(prefix.token + " prefix " + violation)
                .build();
    }

    /**
     * Checks if a cookie name has one of the RFC 6265bis security prefixes.
     *
     * <p>All four prefixes are covered - {@code __Host-}, {@code __Secure-}, {@code __Http-} and
     * {@code __HostHttp-} - and the token is matched ASCII case-insensitively.</p>
     *
     * @param cookieName The cookie name to check
     * @return true if the name carries any security prefix
     */
    public static boolean hasSecurityPrefix(@Nullable String cookieName) {
        return SecurityPrefix.match(cookieName).isPresent();
    }

    /**
     * Checks if a cookie name has the {@code __Host-} prefix, matched ASCII case-insensitively.
     *
     * @param cookieName The cookie name to check
     * @return true if the name carries the {@code __Host-} prefix
     */
    public static boolean hasHostPrefix(@Nullable String cookieName) {
        return SecurityPrefix.match(cookieName).filter(SecurityPrefix.HOST::equals).isPresent();
    }

    /**
     * Checks if a cookie name has the {@code __Secure-} prefix, matched ASCII case-insensitively.
     *
     * @param cookieName The cookie name to check
     * @return true if the name carries the {@code __Secure-} prefix
     */
    public static boolean hasSecurePrefix(@Nullable String cookieName) {
        return SecurityPrefix.match(cookieName).filter(SecurityPrefix.SECURE::equals).isPresent();
    }
}
