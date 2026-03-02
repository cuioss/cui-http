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
package de.cuioss.http.security.config;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable class representing security configuration for HTTP validation.
 *
 * <p>This class encapsulates all security policies and settings needed to configure
 * HTTP security validators. It provides a type-safe, immutable configuration object
 * that can be shared across multiple validation operations.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - Configuration cannot be modified once created</li>
 *   <li><strong>Type Safety</strong> - Strongly typed configuration parameters</li>
 *   <li><strong>Complete Coverage</strong> - Covers all aspects of HTTP security validation</li>
 *   <li><strong>Composability</strong> - Easy to combine with builder patterns</li>
 *   <li><strong>Performance</strong> - Pre-processes sets for O(1) case-insensitive lookups</li>
 * </ul>
 *
 * <h3>Configuration Categories</h3>
 * <ul>
 *   <li><strong>Path Security</strong> - Path traversal prevention, allowed patterns</li>
 *   <li><strong>Parameter Security</strong> - Query parameter validation rules</li>
 *   <li><strong>Header Security</strong> - HTTP header validation policies</li>
 *   <li><strong>Cookie Security</strong> - Cookie validation and security requirements</li>
 *   <li><strong>Body Security</strong> - Request/response body validation settings</li>
 *   <li><strong>Encoding Security</strong> - URL encoding and character validation</li>
 *   <li><strong>Length Limits</strong> - Size restrictions for various HTTP components</li>
 *   <li><strong>General Policies</strong> - Cross-cutting security concerns</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create with builder
 * SecurityConfiguration config = SecurityConfiguration.builder()
 *     .maxPathLength(2048)
 *     .allowPathTraversal(false)
 *     .maxParameterCount(100)
 *     .requireSecureCookies(true)
 *     .build();
 *
 * // Use in validation
 * PathValidator validator = new PathValidator(config);
 * validator.validate("/api/users/123");
 *
 * // Create restrictive configuration
 * SecurityConfiguration strict = SecurityConfiguration.strict();
 *
 * // Create permissive configuration
 * SecurityConfiguration lenient = SecurityConfiguration.lenient();
 * </pre>
 *
 * Implements: Task C1 from HTTP verification specification
 *
 * @since 1.0
 * @see SecurityConfigurationBuilder
 */
@SuppressWarnings("javaarchitecture:S7027")
public final class SecurityConfiguration {

    // Path Security
    private final int maxPathLength;
    private final boolean allowPathTraversal;
    private final boolean allowDoubleEncoding;

    // Parameter Security
    private final int maxParameterCount;
    private final int maxParameterNameLength;
    private final int maxParameterValueLength;

    // Header Security
    private final int maxHeaderCount;
    private final int maxHeaderNameLength;
    private final int maxHeaderValueLength;
    private final @Nullable Set<String> allowedHeaderNames;
    private final Set<String> blockedHeaderNames;

    // Cookie Security
    private final int maxCookieCount;
    private final int maxCookieNameLength;
    private final int maxCookieValueLength;
    private final boolean requireSecureCookies;
    private final boolean requireHttpOnlyCookies;

    // Body Security
    private final long maxBodySize;
    private final @Nullable Set<String> allowedContentTypes;
    private final Set<String> blockedContentTypes;

    // Encoding Security
    private final boolean allowNullBytes;
    private final boolean allowControlCharacters;
    private final boolean allowExtendedAscii;
    private final boolean normalizeUnicode;

    // General Policies
    private final boolean caseSensitiveComparison;
    private final boolean failOnSuspiciousPatterns;
    private final boolean logSecurityViolations;

    // Pre-processed lowercase sets for O(1) case-insensitive lookups
    // Only populated when caseSensitiveComparison is false
    private final @Nullable Set<String> allowedHeaderNamesLowercase;
    private final Set<String> blockedHeaderNamesLowercase;
    private final @Nullable Set<String> allowedContentTypesLowercase;
    private final Set<String> blockedContentTypesLowercase;

    /**
     * Creates a SecurityConfiguration with validation of constraints.
     */
    @SuppressWarnings({"java:S107", "java:S3776"}) // S107: Constructor has many parameters by design - using Builder pattern for construction
    // S3776: Cognitive complexity is from necessary validation in security-critical code
    SecurityConfiguration(
            int maxPathLength,
            boolean allowPathTraversal,
            boolean allowDoubleEncoding,
            int maxParameterCount,
            int maxParameterNameLength,
            int maxParameterValueLength,
            int maxHeaderCount,
            int maxHeaderNameLength,
            int maxHeaderValueLength,
            @Nullable Set<String> allowedHeaderNames,
            Set<String> blockedHeaderNames,
            int maxCookieCount,
            int maxCookieNameLength,
            int maxCookieValueLength,
            boolean requireSecureCookies,
            boolean requireHttpOnlyCookies,
            long maxBodySize,
            @Nullable Set<String> allowedContentTypes,
            Set<String> blockedContentTypes,
            boolean allowNullBytes,
            boolean allowControlCharacters,
            boolean allowExtendedAscii,
            boolean normalizeUnicode,
            boolean caseSensitiveComparison,
            boolean failOnSuspiciousPatterns,
            boolean logSecurityViolations) {

        // Validate length limits are positive
        if (maxPathLength <= 0) {
            throw new IllegalArgumentException("maxPathLength must be positive, got: " + maxPathLength);
        }
        if (maxParameterCount < 0) {
            throw new IllegalArgumentException("maxParameterCount must be non-negative, got: " + maxParameterCount);
        }
        if (maxParameterNameLength <= 0) {
            throw new IllegalArgumentException("maxParameterNameLength must be positive, got: " + maxParameterNameLength);
        }
        if (maxParameterValueLength <= 0) {
            throw new IllegalArgumentException("maxParameterValueLength must be positive, got: " + maxParameterValueLength);
        }
        if (maxHeaderCount < 0) {
            throw new IllegalArgumentException("maxHeaderCount must be non-negative, got: " + maxHeaderCount);
        }
        if (maxHeaderNameLength <= 0) {
            throw new IllegalArgumentException("maxHeaderNameLength must be positive, got: " + maxHeaderNameLength);
        }
        if (maxHeaderValueLength <= 0) {
            throw new IllegalArgumentException("maxHeaderValueLength must be positive, got: " + maxHeaderValueLength);
        }
        if (maxCookieCount < 0) {
            throw new IllegalArgumentException("maxCookieCount must be non-negative, got: " + maxCookieCount);
        }
        if (maxCookieNameLength <= 0) {
            throw new IllegalArgumentException("maxCookieNameLength must be positive, got: " + maxCookieNameLength);
        }
        if (maxCookieValueLength <= 0) {
            throw new IllegalArgumentException("maxCookieValueLength must be positive, got: " + maxCookieValueLength);
        }
        if (maxBodySize < 0) {
            throw new IllegalArgumentException("maxBodySize must be non-negative, got: " + maxBodySize);
        }

        // Assign final fields
        this.maxPathLength = maxPathLength;
        this.allowPathTraversal = allowPathTraversal;
        this.allowDoubleEncoding = allowDoubleEncoding;
        this.maxParameterCount = maxParameterCount;
        this.maxParameterNameLength = maxParameterNameLength;
        this.maxParameterValueLength = maxParameterValueLength;
        this.maxHeaderCount = maxHeaderCount;
        this.maxHeaderNameLength = maxHeaderNameLength;
        this.maxHeaderValueLength = maxHeaderValueLength;
        this.maxCookieCount = maxCookieCount;
        this.maxCookieNameLength = maxCookieNameLength;
        this.maxCookieValueLength = maxCookieValueLength;
        this.requireSecureCookies = requireSecureCookies;
        this.requireHttpOnlyCookies = requireHttpOnlyCookies;
        this.maxBodySize = maxBodySize;
        this.allowNullBytes = allowNullBytes;
        this.allowControlCharacters = allowControlCharacters;
        this.allowExtendedAscii = allowExtendedAscii;
        this.normalizeUnicode = normalizeUnicode;
        this.caseSensitiveComparison = caseSensitiveComparison;
        this.failOnSuspiciousPatterns = failOnSuspiciousPatterns;
        this.logSecurityViolations = logSecurityViolations;

        // Ensure sets are immutable and non-null
        this.allowedHeaderNames = allowedHeaderNames != null ?
                Set.copyOf(allowedHeaderNames) : null;
        this.blockedHeaderNames = Set.copyOf(blockedHeaderNames);
        this.allowedContentTypes = allowedContentTypes != null ?
                Set.copyOf(allowedContentTypes) : null;
        this.blockedContentTypes = Set.copyOf(blockedContentTypes);

        // Pre-process sets for case-insensitive comparison if needed
        // This optimization changes lookups from O(n) to O(1) average case
        if (!caseSensitiveComparison) {
            // Convert all strings to lowercase for case-insensitive lookups
            this.allowedHeaderNamesLowercase = this.allowedHeaderNames != null ?
                    this.allowedHeaderNames.stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toUnmodifiableSet()) : null;
            this.blockedHeaderNamesLowercase = this.blockedHeaderNames.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
            this.allowedContentTypesLowercase = this.allowedContentTypes != null ?
                    this.allowedContentTypes.stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toUnmodifiableSet()) : null;
            this.blockedContentTypesLowercase = this.blockedContentTypes.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            // Not needed for case-sensitive comparison
            this.allowedHeaderNamesLowercase = null;
            this.blockedHeaderNamesLowercase = Set.of();
            this.allowedContentTypesLowercase = null;
            this.blockedContentTypesLowercase = Set.of();
        }
    }

    /**
     * Creates a builder for constructing SecurityConfiguration instances.
     *
     * @return A new SecurityConfigurationBuilder with default values
     */
    public static SecurityConfigurationBuilder builder() {
        return new SecurityConfigurationBuilder();
    }

    /**
     * Creates a strict security configuration with tight restrictions.
     * This configuration prioritizes security over compatibility.
     *
     * @return A SecurityConfiguration with strict security policies
     */
    public static SecurityConfiguration strict() {
        return builder()
                // Path Security - very restrictive
                .maxPathLength(1024)
                .allowPathTraversal(false)
                .allowDoubleEncoding(false)

                // Parameter Security - strict limits
                .maxParameterCount(50)
                .maxParameterNameLength(100)
                .maxParameterValueLength(1024)

                // Header Security - conservative limits
                .maxHeaderCount(50)
                .maxHeaderNameLength(100)
                .maxHeaderValueLength(4096)

                // Cookie Security - require all security flags
                .maxCookieCount(20)
                .maxCookieNameLength(100)
                .maxCookieValueLength(4096)
                .requireSecureCookies(true)
                .requireHttpOnlyCookies(true)

                // Body Security - reasonable limit
                .maxBodySize(10_485_760) // 10MB

                // Encoding Security - no dangerous characters
                .allowNullBytes(false)
                .allowControlCharacters(false)
                .allowExtendedAscii(false)
                .normalizeUnicode(true)

                // General Policies - maximum security
                .caseSensitiveComparison(true)
                .failOnSuspiciousPatterns(true)
                .logSecurityViolations(true)

                .build();
    }

    /**
     * Creates a lenient security configuration for maximum compatibility.
     * This configuration should only be used in trusted environments.
     *
     * @return A SecurityConfiguration with permissive policies
     */
    public static SecurityConfiguration lenient() {
        return builder()
                // Path Security - permissive
                .maxPathLength(8192)
                .allowPathTraversal(true) // WARNING: Security risk
                .allowDoubleEncoding(true)

                // Parameter Security - generous limits
                .maxParameterCount(1000)
                .maxParameterNameLength(512)
                .maxParameterValueLength(8192)

                // Header Security - large limits
                .maxHeaderCount(200)
                .maxHeaderNameLength(512)
                .maxHeaderValueLength(16384)

                // Cookie Security - no requirements
                .maxCookieCount(100)
                .maxCookieNameLength(512)
                .maxCookieValueLength(8192)
                .requireSecureCookies(false)
                .requireHttpOnlyCookies(false)

                // Body Security - large limit
                .maxBodySize(104_857_600) // 100MB

                // Encoding Security - allow everything
                .allowNullBytes(true)
                .allowControlCharacters(true)
                .allowExtendedAscii(true)
                .normalizeUnicode(false)

                // General Policies - minimal security
                .caseSensitiveComparison(false)
                .failOnSuspiciousPatterns(false)
                .logSecurityViolations(false)

                .build();
    }

    /**
     * Creates a security configuration with default balanced settings.
     *
     * @return A SecurityConfiguration with default security policies
     */
    public static SecurityConfiguration defaults() {
        return builder().build();
    }

    /**
     * Checks if the configuration allows a specific header name.
     *
     * @param headerName The header name to check (null returns false)
     * @return true if the header is allowed, false if blocked or null
     */
    public boolean isHeaderAllowed(@Nullable String headerName) {
        return isAllowed(headerName, allowedHeaderNames, blockedHeaderNames,
                allowedHeaderNamesLowercase, blockedHeaderNamesLowercase);
    }

    /**
     * Checks if the configuration allows a specific content type.
     *
     * @param contentType The content type to check (null returns false)
     * @return true if the content type is allowed, false if blocked or null
     */
    public boolean isContentTypeAllowed(@Nullable String contentType) {
        return isAllowed(contentType, allowedContentTypes, blockedContentTypes,
                allowedContentTypesLowercase, blockedContentTypesLowercase);
    }

    /**
     * Helper method to check if a value is allowed based on allow and block lists.
     * For case-insensitive comparison, uses pre-processed lowercase sets for O(1) lookups
     * instead of O(n) stream operations.
     *
     * @param value The value to check (null returns false)
     * @param allowedSet The set of allowed values (null means all allowed)
     * @param blockedSet The set of blocked values
     * @param allowedSetLowercase Pre-processed lowercase allowed set (used when case-insensitive)
     * @param blockedSetLowercase Pre-processed lowercase blocked set (used when case-insensitive)
     * @return true if the value is allowed, false if blocked or null
     */
    private boolean isAllowed(@Nullable String value,
            @Nullable Set<String> allowedSet,
            Set<String> blockedSet,
            @Nullable Set<String> allowedSetLowercase,
            Set<String> blockedSetLowercase) {
        if (value == null) {
            return false;
        }

        // For case-sensitive comparison, use original sets with O(1) contains
        if (caseSensitiveComparison) {
            // Check blocked list first
            if (blockedSet.contains(value)) {
                return false;
            }
            // If there's an allow list, check it
            if (allowedSet != null) {
                return allowedSet.contains(value);
            }
            // No allow list means all values are allowed (except blocked ones)
            return true;
        }

        // For case-insensitive comparison, use pre-processed lowercase sets
        // This provides O(1) average case performance instead of O(n)
        String valueLowercase = value.toLowerCase();

        // Check blocked list first using O(1) contains
        if (blockedSetLowercase.contains(valueLowercase)) {
            return false;
        }

        // If there's an allow list, check it using O(1) contains
        if (allowedSetLowercase != null) {
            return allowedSetLowercase.contains(valueLowercase);
        }

        // No allow list means all values are allowed (except blocked ones)
        return true;
    }

    /**
     * Checks if this configuration is considered "strict" based on key security settings.
     *
     * @return true if this configuration uses strict security policies
     */
    public boolean isStrict() {
        return !allowPathTraversal &&
                !allowDoubleEncoding &&
                !allowNullBytes &&
                !allowControlCharacters &&
                !allowExtendedAscii &&
                normalizeUnicode &&
                failOnSuspiciousPatterns;
    }

    /**
     * Checks if this configuration is considered "lenient" based on key security settings.
     *
     * @return true if this configuration uses lenient security policies
     */
    public boolean isLenient() {
        return allowPathTraversal &&
                allowDoubleEncoding &&
                allowNullBytes &&
                allowControlCharacters &&
                allowExtendedAscii &&
                !normalizeUnicode &&
                !failOnSuspiciousPatterns;
    }

    // Getters for all fields

    public int maxPathLength() {
        return maxPathLength;
    }

    public boolean allowPathTraversal() {
        return allowPathTraversal;
    }

    public boolean allowDoubleEncoding() {
        return allowDoubleEncoding;
    }

    public int maxParameterCount() {
        return maxParameterCount;
    }

    public int maxParameterNameLength() {
        return maxParameterNameLength;
    }

    public int maxParameterValueLength() {
        return maxParameterValueLength;
    }

    public int maxHeaderCount() {
        return maxHeaderCount;
    }

    public int maxHeaderNameLength() {
        return maxHeaderNameLength;
    }

    public int maxHeaderValueLength() {
        return maxHeaderValueLength;
    }

    public @Nullable Set<String> allowedHeaderNames() {
        return allowedHeaderNames;
    }

    public Set<String> blockedHeaderNames() {
        return blockedHeaderNames;
    }

    public int maxCookieCount() {
        return maxCookieCount;
    }

    public int maxCookieNameLength() {
        return maxCookieNameLength;
    }

    public int maxCookieValueLength() {
        return maxCookieValueLength;
    }

    public boolean requireSecureCookies() {
        return requireSecureCookies;
    }

    public boolean requireHttpOnlyCookies() {
        return requireHttpOnlyCookies;
    }

    public long maxBodySize() {
        return maxBodySize;
    }

    public @Nullable Set<String> allowedContentTypes() {
        return allowedContentTypes;
    }

    public Set<String> blockedContentTypes() {
        return blockedContentTypes;
    }

    public boolean allowNullBytes() {
        return allowNullBytes;
    }

    public boolean allowControlCharacters() {
        return allowControlCharacters;
    }

    public boolean allowExtendedAscii() {
        return allowExtendedAscii;
    }

    public boolean normalizeUnicode() {
        return normalizeUnicode;
    }

    public boolean caseSensitiveComparison() {
        return caseSensitiveComparison;
    }

    public boolean failOnSuspiciousPatterns() {
        return failOnSuspiciousPatterns;
    }

    public boolean logSecurityViolations() {
        return logSecurityViolations;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SecurityConfiguration other)) return false;

        return maxPathLength == other.maxPathLength &&
                allowPathTraversal == other.allowPathTraversal &&
                allowDoubleEncoding == other.allowDoubleEncoding &&
                maxParameterCount == other.maxParameterCount &&
                maxParameterNameLength == other.maxParameterNameLength &&
                maxParameterValueLength == other.maxParameterValueLength &&
                maxHeaderCount == other.maxHeaderCount &&
                maxHeaderNameLength == other.maxHeaderNameLength &&
                maxHeaderValueLength == other.maxHeaderValueLength &&
                maxCookieCount == other.maxCookieCount &&
                maxCookieNameLength == other.maxCookieNameLength &&
                maxCookieValueLength == other.maxCookieValueLength &&
                requireSecureCookies == other.requireSecureCookies &&
                requireHttpOnlyCookies == other.requireHttpOnlyCookies &&
                maxBodySize == other.maxBodySize &&
                allowNullBytes == other.allowNullBytes &&
                allowControlCharacters == other.allowControlCharacters &&
                allowExtendedAscii == other.allowExtendedAscii &&
                normalizeUnicode == other.normalizeUnicode &&
                caseSensitiveComparison == other.caseSensitiveComparison &&
                failOnSuspiciousPatterns == other.failOnSuspiciousPatterns &&
                logSecurityViolations == other.logSecurityViolations &&
                Objects.equals(allowedHeaderNames, other.allowedHeaderNames) &&
                Objects.equals(blockedHeaderNames, other.blockedHeaderNames) &&
                Objects.equals(allowedContentTypes, other.allowedContentTypes) &&
                Objects.equals(blockedContentTypes, other.blockedContentTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxPathLength, allowPathTraversal, allowDoubleEncoding,
                maxParameterCount, maxParameterNameLength, maxParameterValueLength,
                maxHeaderCount, maxHeaderNameLength, maxHeaderValueLength,
                allowedHeaderNames, blockedHeaderNames,
                maxCookieCount, maxCookieNameLength, maxCookieValueLength,
                requireSecureCookies, requireHttpOnlyCookies,
                maxBodySize, allowedContentTypes, blockedContentTypes,
                allowNullBytes, allowControlCharacters, allowExtendedAscii, normalizeUnicode,
                caseSensitiveComparison, failOnSuspiciousPatterns, logSecurityViolations
        );
    }

    @Override
    public String toString() {
        return "SecurityConfiguration{" +
                "maxPathLength=" + maxPathLength +
                ", allowPathTraversal=" + allowPathTraversal +
                ", allowDoubleEncoding=" + allowDoubleEncoding +
                ", maxParameterCount=" + maxParameterCount +
                ", maxParameterNameLength=" + maxParameterNameLength +
                ", maxParameterValueLength=" + maxParameterValueLength +
                ", maxHeaderCount=" + maxHeaderCount +
                ", maxHeaderNameLength=" + maxHeaderNameLength +
                ", maxHeaderValueLength=" + maxHeaderValueLength +
                ", allowedHeaderNames=" + allowedHeaderNames +
                ", blockedHeaderNames=" + blockedHeaderNames +
                ", maxCookieCount=" + maxCookieCount +
                ", maxCookieNameLength=" + maxCookieNameLength +
                ", maxCookieValueLength=" + maxCookieValueLength +
                ", requireSecureCookies=" + requireSecureCookies +
                ", requireHttpOnlyCookies=" + requireHttpOnlyCookies +
                ", maxBodySize=" + maxBodySize +
                ", allowedContentTypes=" + allowedContentTypes +
                ", blockedContentTypes=" + blockedContentTypes +
                ", allowNullBytes=" + allowNullBytes +
                ", allowControlCharacters=" + allowControlCharacters +
                ", allowExtendedAscii=" + allowExtendedAscii +
                ", normalizeUnicode=" + normalizeUnicode +
                ", caseSensitiveComparison=" + caseSensitiveComparison +
                ", failOnSuspiciousPatterns=" + failOnSuspiciousPatterns +
                ", logSecurityViolations=" + logSecurityViolations +
                '}';
    }
}