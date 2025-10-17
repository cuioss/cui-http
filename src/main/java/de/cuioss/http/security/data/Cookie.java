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
package de.cuioss.http.security.data;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.validation.CharacterValidationStage;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Immutable record representing an HTTP cookie with name, value, and attributes.
 *
 * <p>This record encapsulates the structure of HTTP cookies as defined in RFC 6265,
 * providing a type-safe way to handle cookie data in HTTP security validation.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All fields are final and the record cannot be modified</li>
 *   <li><strong>RFC Compliance</strong> - Follows HTTP cookie specifications</li>
 *   <li><strong>Security Focus</strong> - Designed with security validation in mind</li>
 *   <li><strong>Flexibility</strong> - Supports various cookie attribute formats</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Simple cookie
 * Cookie sessionCookie = new Cookie("JSESSIONID", "ABC123", "");
 *
 * // Cookie with attributes
 * Cookie secureCookie = new Cookie(
 *     "auth_token",
 *     "xyz789",
 *     "Domain=example.com; Path=/; Secure; HttpOnly"
 * );
 *
 * // Access components
 * String name = cookie.name();         // "JSESSIONID"
 * String value = cookie.value();       // "ABC123"
 * String attrs = cookie.attributes();  // "Domain=..."
 *
 * // Check for security attributes
 * boolean isSecure = cookie.isSecure();       // Check for Secure attribute
 * boolean isHttpOnly = cookie.isHttpOnly();   // Check for HttpOnly attribute
 *
 * // Use in validation
 * validator.validate(cookie.name(), ValidationType.COOKIE_NAME);
 * validator.validate(cookie.value(), ValidationType.COOKIE_VALUE);
 * </pre>
 *
 * <h3>Cookie Attributes</h3>
 * <p>The attributes field contains the semicolon-separated list of cookie attributes
 * such as Domain, Path, Secure, HttpOnly, SameSite, and Max-Age. This field can be
 * an empty string if no attributes are present.</p>
 *
 * <h3>Security Considerations</h3>
 * <p>This record is a simple data container. Security validation should be applied
 * to the name, value, and attributes components separately using appropriate validators.</p>
 *
 * Implements: Task B3 from HTTP verification specification
 *
 * @param name The cookie name (e.g., "JSESSIONID", "auth_token")
 * @param value The cookie value (e.g., session ID, authentication token)
 * @param attributes Cookie attributes string (e.g., "Domain=example.com; Secure; HttpOnly")
 *
 * @since 1.0
 * @see ValidationType#COOKIE_NAME
 * @see ValidationType#COOKIE_VALUE
 */
public record Cookie(@Nullable
    String name, @Nullable
    String value, @Nullable
    String attributes) {

    /**
     * Shared validator for cookie name suffixes using default security configuration.
     * Validates cookie name characters to prevent injection attacks in factory methods.
     */
    private static final CharacterValidationStage COOKIE_NAME_VALIDATOR =
            new CharacterValidationStage(
                    SecurityConfiguration.builder().build(),
                    ValidationType.COOKIE_NAME);

    /**
     * Creates a simple cookie with no attributes.
     *
     * @param name The cookie name
     * @param value The cookie value
     * @return A Cookie with no attributes
     */
    public static Cookie simple(String name, String value) {
        return new Cookie(name, value, "");
    }

    /**
     * Creates a __Host- prefix cookie with RFC 6265bis compliant attributes.
     *
     * <p>Creates a cookie with the __Host- prefix that meets all RFC requirements:</p>
     * <ul>
     *   <li>Name starts with __Host-</li>
     *   <li>Has Secure attribute</li>
     *   <li>Has Path=/ attribute</li>
     *   <li>No Domain attribute (bound to exact host)</li>
     *   <li>SameSite=Strict (strongest CSRF protection for single-host cookies)</li>
     * </ul>
     *
     * <p><strong>Security Note:</strong> The returned cookie structure is valid, but applications
     * should validate it using {@link de.cuioss.http.security.validation.CookiePrefixValidationStage}
     * to ensure all prefix requirements are met.</p>
     *
     * <h3>Usage Example</h3>
     * <pre>
     * // Create a __Host- cookie
     * Cookie sessionCookie = Cookie.hostPrefix("session", "abc123xyz");
     *
     * // Validate the cookie (recommended)
     * CookiePrefixValidationStage validator = new CookiePrefixValidationStage();
     * validator.validateCookie(sessionCookie);
     *
     * // Use in HTTP response
     * response.setHeader("Set-Cookie", sessionCookie.toCookieString());
     * </pre>
     *
     * @param suffix The cookie name suffix (will be prefixed with __Host-)
     * @param value The cookie value
     * @return A Cookie with __Host- prefix and compliant attributes
     * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis">RFC 6265bis</a>
     * @since 1.0
     */
    public static Cookie hostPrefix(String suffix, String value) {
        // Validate suffix to prevent injection of invalid characters
        COOKIE_NAME_VALIDATOR.validate(suffix);
        return new Cookie("__Host-" + suffix, value, "Secure; Path=/; HttpOnly; SameSite=Strict");
    }

    /**
     * Creates a __Secure- prefix cookie with RFC 6265bis compliant attributes.
     *
     * <p>Creates a cookie with the __Secure- prefix that meets RFC requirements:</p>
     * <ul>
     *   <li>Name starts with __Secure-</li>
     *   <li>Has Secure attribute</li>
     *   <li>SameSite=Lax (CSRF protection with cross-subdomain navigation support)</li>
     * </ul>
     *
     * <p>Unlike __Host- cookies, __Secure- cookies may have Domain and Path attributes,
     * making them suitable for cross-subdomain scenarios where SameSite=Lax prevents
     * breaking top-level navigation while maintaining CSRF protection.</p>
     *
     * <p><strong>Security Note:</strong> The returned cookie structure is valid, but applications
     * should validate it using {@link de.cuioss.http.security.validation.CookiePrefixValidationStage}
     * to ensure all prefix requirements are met.</p>
     *
     * <h3>Usage Example</h3>
     * <pre>
     * // Create a __Secure- cookie
     * Cookie tokenCookie = Cookie.securePrefix("token", "xyz789");
     *
     * // Validate the cookie (recommended)
     * CookiePrefixValidationStage validator = new CookiePrefixValidationStage();
     * validator.validateCookie(tokenCookie);
     *
     * // Use in HTTP response
     * response.setHeader("Set-Cookie", tokenCookie.toCookieString());
     * </pre>
     *
     * @param suffix The cookie name suffix (will be prefixed with __Secure-)
     * @param value The cookie value
     * @return A Cookie with __Secure- prefix and compliant attributes
     * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis">RFC 6265bis</a>
     * @since 1.0
     */
    public static Cookie securePrefix(String suffix, String value) {
        // Validate suffix to prevent injection of invalid characters
        COOKIE_NAME_VALIDATOR.validate(suffix);
        return new Cookie("__Secure-" + suffix, value, "Secure; HttpOnly; SameSite=Lax");
    }

    /**
     * Checks if this cookie has a non-null, non-empty name.
     *
     * @return true if the name is not null and not empty
     */
    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    /**
     * Checks if this cookie has a non-null, non-empty value.
     *
     * @return true if the value is not null and not empty
     */
    public boolean hasValue() {
        return value != null && !value.isEmpty();
    }

    /**
     * Checks if this cookie has any attributes.
     *
     * @return true if the attributes string is not null and not empty
     */
    public boolean hasAttributes() {
        return attributes != null && !attributes.isEmpty();
    }

    /**
     * Checks if the cookie has the Secure attribute.
     *
     * @return true if the attributes contain "Secure"
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isSecure() {
        return hasAttributes() && attributes.toLowerCase().contains("secure");
    }

    /**
     * Checks if the cookie has the HttpOnly attribute.
     *
     * @return true if the attributes contain "HttpOnly"
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isHttpOnly() {
        return hasAttributes() && attributes.toLowerCase().contains("httponly");
    }

    /**
     * Extracts the Domain attribute value if present.
     *
     * @return The domain value wrapped in Optional, or empty if not specified
     */
    public Optional<String> getDomain() {
        return extractAttributeValue("domain");
    }

    /**
     * Extracts the Path attribute value if present.
     *
     * @return The path value wrapped in Optional, or empty if not specified
     */
    public Optional<String> getPath() {
        return extractAttributeValue("path");
    }

    /**
     * Extracts the SameSite attribute value if present.
     *
     * @return The SameSite value (e.g., "Strict", "Lax", "None") wrapped in Optional, or empty if not specified
     */
    public Optional<String> getSameSite() {
        return extractAttributeValue("samesite");
    }

    /**
     * Extracts the Max-Age attribute value if present.
     *
     * @return The Max-Age value as a string wrapped in Optional, or empty if not specified
     */
    public Optional<String> getMaxAge() {
        return extractAttributeValue("max-age");
    }

    /**
     * Extracts a specific attribute value from the attributes string.
     *
     * @param attributeName The name of the attribute (case-insensitive)
     * @return The attribute value or null if not found
     */
    private Optional<String> extractAttributeValue(String attributeName) {
        if (!hasAttributes()) {
            return Optional.empty();
        }
        return AttributeParser.extractAttributeValue(attributes, attributeName);
    }

    /**
     * Returns all attribute names present in this cookie.
     *
     * @return A list of attribute names (may be empty)
     */
    @SuppressWarnings("ConstantConditions")
    public List<String> getAttributeNames() {
        if (!hasAttributes()) {
            return List.of();
        }
        return Arrays.stream(attributes.split(";"))
                .map(String::trim)
                .filter(attr -> !attr.isEmpty())
                .map(attr -> {
                    int equalIndex = attr.indexOf('=');
                    return equalIndex > 0 ? attr.substring(0, equalIndex).trim() : attr;
                })
                .toList();
    }

    public String nameOrDefault(String defaultName) {
        return name != null ? name : defaultName;
    }

    public String valueOrDefault(String defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Returns a string representation suitable for HTTP Set-Cookie headers.
     * Note: This does not perform proper HTTP encoding - use appropriate
     * encoding utilities for actual HTTP header generation.
     *
     * @return A string in the format "name=value; attributes"
     */
    public String toCookieString() {
        StringBuilder sb = new StringBuilder();

        if (name != null) {
            sb.append(name);
        }

        sb.append("=");

        if (value != null) {
            sb.append(value);
        }

        if (hasAttributes()) {
            sb.append("; ").append(attributes);
        }

        return sb.toString();
    }

    /**
     * Returns a copy of this cookie with a new name.
     *
     * @param newName The new cookie name
     * @return A new Cookie with the specified name and same value/attributes
     */
    public Cookie withName(String newName) {
        return new Cookie(newName, value, attributes);
    }

    /**
     * Returns a copy of this cookie with a new value.
     *
     * @param newValue The new cookie value
     * @return A new Cookie with the same name/attributes and specified value
     */
    public Cookie withValue(String newValue) {
        return new Cookie(name, newValue, attributes);
    }

    /**
     * Returns a copy of this cookie with new attributes.
     *
     * @param newAttributes The new attributes string
     * @return A new Cookie with the same name/value and specified attributes
     */
    public Cookie withAttributes(String newAttributes) {
        return new Cookie(name, value, newAttributes);
    }
}