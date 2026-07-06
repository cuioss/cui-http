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
import de.cuioss.http.security.exceptions.UrlSecurityException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Allow/block-list enforcement stage for single-value HTTP components (header names, content types).
 *
 * <p>A single header name or content-type value <em>is</em> checkable against a set - correcting
 * PR #74's over-broad claim that allow/block lists "fundamentally can't be enforced by single-value
 * validation pipelines". This stage restores that enforcement: it tests one value against the
 * configured, <strong>case-insensitive</strong> allow and block lists (precomputed to lowercase at
 * construction, mirroring the lookup sets #74 deleted).</p>
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li><strong>Block-list precedence</strong> - a value present in the block-list is rejected
 *       regardless of the allow-list.</li>
 *   <li><strong>Empty allow-list = allow-all</strong> - an empty allow-list imposes no restriction
 *       (it does <em>not</em> deny-all); a non-empty allow-list rejects any value not in it.</li>
 *   <li><strong>Case-insensitive</strong> - comparison is done on the lowercased value.</li>
 * </ul>
 *
 * <p>Use {@link #forHeaderNames(SecurityConfiguration)} for the header-name lists (wired into the
 * header-name pipeline) and {@link #forContentTypes(SecurityConfiguration)} for the content-type
 * lists.</p>
 *
 * @since 1.0
 */
@EqualsAndHashCode
@ToString
public final class AllowBlockListStage implements HttpSecurityValidator {

    private final Set<String> allowedLowercase;
    private final Set<String> blockedLowercase;
    private final ValidationType validationType;
    private final boolean mediaTypeOnly;

    /**
     * Creates an allow/block-list stage that matches the whole value.
     *
     * @param allowed the allow-list (empty = allow-all); compared case-insensitively
     * @param blocked the block-list (takes precedence); compared case-insensitively
     * @param validationType the validation type used in emitted exceptions
     * @throws NullPointerException if any argument is null
     */
    public AllowBlockListStage(Set<String> allowed, Set<String> blocked, ValidationType validationType) {
        this(allowed, blocked, validationType, false);
    }

    /**
     * Creates an allow/block-list stage.
     *
     * @param allowed the allow-list (empty = allow-all); compared case-insensitively
     * @param blocked the block-list (takes precedence); compared case-insensitively
     * @param validationType the validation type used in emitted exceptions
     * @param mediaTypeOnly when {@code true}, only the media type of the value is matched: any
     *        parameters (everything from the first {@code ;}) and surrounding whitespace are
     *        stripped before comparison, so {@code application/json; charset=UTF-8} matches an
     *        {@code application/json} list entry
     * @throws NullPointerException if any argument is null
     */
    public AllowBlockListStage(Set<String> allowed, Set<String> blocked, ValidationType validationType,
            boolean mediaTypeOnly) {
        Objects.requireNonNull(allowed, "allowed must not be null");
        Objects.requireNonNull(blocked, "blocked must not be null");
        this.validationType = Objects.requireNonNull(validationType, "validationType must not be null");
        this.mediaTypeOnly = mediaTypeOnly;
        this.allowedLowercase = toLowercaseSet(allowed);
        this.blockedLowercase = toLowercaseSet(blocked);
    }

    private static Set<String> toLowercaseSet(Set<String> source) {
        Set<String> lower = HashSet.newHashSet(source.size());
        for (String value : source) {
            lower.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(lower);
    }

    /**
     * Creates a stage enforcing the header-name allow/block lists from the configuration.
     *
     * @param config the security configuration
     * @return a header-name allow/block-list stage
     */
    public static AllowBlockListStage forHeaderNames(SecurityConfiguration config) {
        Objects.requireNonNull(config, "config must not be null");
        return new AllowBlockListStage(config.allowedHeaderNames(), config.blockedHeaderNames(),
                ValidationType.HEADER_NAME);
    }

    /**
     * Creates a stage enforcing the content-type allow/block lists from the configuration.
     *
     * @param config the security configuration
     * @return a content-type allow/block-list stage
     */
    public static AllowBlockListStage forContentTypes(SecurityConfiguration config) {
        Objects.requireNonNull(config, "config must not be null");
        // Content types travel as a header value; use HEADER_VALUE as the reported type.
        // Match on media type only so parameters (e.g. "; charset=UTF-8") do not defeat the lists.
        return new AllowBlockListStage(config.allowedContentTypes(), config.blockedContentTypes(),
                ValidationType.HEADER_VALUE, true);
    }

    @Override
    public Optional<String> validate(@Nullable String value) throws UrlSecurityException {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isEmpty()) {
            return Optional.of(value);
        }

        String lower = comparisonKey(value);

        if (blockedLowercase.contains(lower)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_INPUT)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Value '" + value + "' is block-listed")
                    .build();
        }

        if (!allowedLowercase.isEmpty() && !allowedLowercase.contains(lower)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_INPUT)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Value '" + value + "' is not in the allow-list")
                    .build();
        }

        return Optional.of(value);
    }

    /**
     * Computes the lowercase key used for allow/block-list membership. In {@code mediaTypeOnly}
     * mode the media type is isolated by dropping any parameters (from the first {@code ;}) and
     * trimming surrounding whitespace.
     */
    private String comparisonKey(String value) {
        String candidate = value;
        if (mediaTypeOnly) {
            int semicolon = candidate.indexOf(';');
            if (semicolon >= 0) {
                candidate = candidate.substring(0, semicolon);
            }
            candidate = candidate.trim();
        }
        return candidate.toLowerCase(Locale.ROOT);
    }
}
