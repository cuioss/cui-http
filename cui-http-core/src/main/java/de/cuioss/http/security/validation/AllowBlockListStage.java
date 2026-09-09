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
import de.cuioss.http.security.exceptions.UrlSecurityException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

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
 *   <li><strong>The empty value is not exempt</strong> - an empty value is evaluated against both
 *       lists like any other value. A configured non-empty allow-list therefore rejects it, and a
 *       block-list containing the empty string rejects it too.</li>
 *   <li><strong>Entries are canonicalised the way values are</strong> - in {@code mediaTypeOnly}
 *       mode the configured entries go through the same media-type isolation at construction that
 *       an incoming value goes through at comparison, so an entry written as
 *       {@code " Application/JSON; charset=utf-8 "} still matches {@code application/json}. In
 *       whole-value mode ({@link #forHeaderNames(SecurityConfiguration)}) entries are lowercased
 *       only.</li>
 *   <li><strong>Malformed entries are rejected at construction</strong> - in {@code mediaTypeOnly}
 *       mode an entry that canonicalises to the empty media type (for example
 *       {@code "; charset=utf-8"} or {@code "  "}) can never match a value carrying one, so it is
 *       rejected with an {@link IllegalArgumentException} naming the offending entry rather than
 *       being silently retained as an unreachable list member.</li>
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

    /**
     * Maximum number of rendered characters {@link #renderForDetail(String)} emits.
     *
     * <p>This is the same limit {@code UrlSecurityException} applies to the detail it renders, and
     * that class is the source of the convention. The value is duplicated rather than shared
     * because sharing it would mean publishing an internal rendering limit on the exception's
     * public API for the sake of one collaborator in another package.</p>
     */
    private static final int MAX_RENDERED_DETAIL_LENGTH = 200;

    /**
     * Marker appended when the rendered value was cut at {@link #MAX_RENDERED_DETAIL_LENGTH}.
     * Duplicated from {@code UrlSecurityException} for the reason given on that constant.
     */
    private static final String TRUNCATION_MARKER = "...";

    /**
     * Code points {@link #renderForDetail(String)} escapes: the C0 controls (U+0000-U+001F), DEL
     * (U+007F), the C1 controls (U+0080-U+009F, notably NEL U+0085) and the Unicode line and
     * paragraph separators U+2028 and U+2029.
     *
     * <p>The expression is character-identical to {@code UrlSecurityException}'s
     * {@code CONTROL_CHARS_PATTERN}, and that class is the source of the convention. It is
     * duplicated rather than shared for the reason given on {@link #MAX_RENDERED_DETAIL_LENGTH}:
     * sharing it would mean publishing an internal rendering detail on the exception's public API
     * - every package here is exported, so there is no package-private route across the
     * {@code exceptions} / {@code validation} split - for the sake of one collaborator.</p>
     *
     * <p>{@code Character.isISOControl} is deliberately <em>not</em> used: it covers only
     * U+0000-U+001F and U+007F-U+009F, so it leaves U+2028 and U+2029 unescaped and the two
     * sanitisers would neutralise different code-point sets.</p>
     */
    private static final Pattern CONTROL_CHARS_PATTERN =
            Pattern.compile("[\\x00-\\x1F\\x7F-\\u009F\\u2028\\u2029]");

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
     * @param mediaTypeOnly when {@code true}, only the media type is matched: any parameters
     *        (everything from the first {@code ;}) and surrounding whitespace are stripped before
     *        comparison, so {@code application/json; charset=UTF-8} matches an
     *        {@code application/json} list entry. The configured entries are put through the same
     *        canonicalisation at construction, so a parameterised or padded entry still matches
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException in {@code mediaTypeOnly} mode, if a list entry canonicalises
     *         to the empty media type (for example {@code "; charset=utf-8"} or {@code "  "})
     */
    public AllowBlockListStage(Set<String> allowed, Set<String> blocked, ValidationType validationType,
            boolean mediaTypeOnly) {
        Objects.requireNonNull(allowed, "allowed must not be null");
        Objects.requireNonNull(blocked, "blocked must not be null");
        this.validationType = Objects.requireNonNull(validationType, "validationType must not be null");
        this.mediaTypeOnly = mediaTypeOnly;
        this.allowedLowercase = canonicaliseEntries(allowed, mediaTypeOnly, "allow-list");
        this.blockedLowercase = canonicaliseEntries(blocked, mediaTypeOnly, "block-list");
    }

    /**
     * Canonicalises the configured entries exactly the way an incoming value is canonicalised, so
     * the two spellings cannot drift apart.
     *
     * @param source the configured entries
     * @param mediaTypeOnly whether media-type isolation applies
     * @param listName the list's name, used verbatim in the rejection message
     * @return the canonicalised entries
     * @throws IllegalArgumentException in {@code mediaTypeOnly} mode, if an entry canonicalises to
     *         the empty media type - such an entry can never match a value that carries one
     */
    private static Set<String> canonicaliseEntries(Set<String> source, boolean mediaTypeOnly, String listName) {
        Set<String> canonical = HashSet.newHashSet(source.size());
        for (String entry : source) {
            String key = canonicalise(entry, mediaTypeOnly);
            if (mediaTypeOnly && key.isEmpty()) {
                throw new IllegalArgumentException("Content-type " + listName + " entry '" + entry
                        + "' canonicalises to the empty media type");
            }
            canonical.add(key);
        }
        return Set.copyOf(canonical);
    }

    /**
     * The single canonicalisation used for both the configured entries and the incoming value: in
     * {@code mediaTypeOnly} mode the media type is isolated by dropping any parameters (from the
     * first {@code ;}) and trimming surrounding whitespace; the result is then lowercased.
     *
     * @param value the entry or incoming value to canonicalise
     * @param mediaTypeOnly whether media-type isolation applies
     * @return the canonical comparison key
     */
    private static String canonicalise(String value, boolean mediaTypeOnly) {
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

        String lower = comparisonKey(value);

        if (blockedLowercase.contains(lower)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_INPUT)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Value '" + renderForDetail(value) + "' is block-listed")
                    .build();
        }

        if (!allowedLowercase.isEmpty() && !allowedLowercase.contains(lower)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_INPUT)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Value '" + renderForDetail(value) + "' is not in the allow-list")
                    .build();
        }

        return Optional.of(value);
    }

    /**
     * Renders a rejected value for the exception detail. Every code point
     * {@link #CONTROL_CHARS_PATTERN} matches is replaced by its escaped {@code U+XXXX} form - the
     * shape {@link CharacterValidationStage} already uses - so a value carrying CR/LF, NEL or a
     * Unicode line/paragraph separator cannot forge a log line through the detail callers log.
     *
     * <p>Escaping is expansive - one control code point renders as six characters - so an
     * unbounded value would be amplified sixfold here and then stored verbatim in the exception's
     * {@code detail} for the exception's lifetime (CWE-400). The pipelines that wire this stage do
     * run a {@code LengthValidationStage} ahead of it, but this stage is public and composable, so
     * its own bound must not rest on a caller's pipeline composition: constructed standalone or
     * chained without a preceding length stage, the value reaching this method can be arbitrarily
     * long. The result is therefore capped at
     * {@link #MAX_RENDERED_DETAIL_LENGTH} characters plus the {@link #TRUNCATION_MARKER} at the
     * source, and the loop exits at the cut so the amplified string is never built. The cut is
     * taken between rendered code points, so a {@code U+XXXX} sequence is never split.</p>
     *
     * @param value the rejected value
     * @return the value with every {@link #CONTROL_CHARS_PATTERN} match escaped, bounded to
     *         {@link #MAX_RENDERED_DETAIL_LENGTH} characters plus the truncation marker
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
     * Computes the key used for allow/block-list membership, through the same
     * {@link #canonicalise(String, boolean)} the configured entries went through at construction.
     */
    private String comparisonKey(String value) {
        return canonicalise(value, mediaTypeOnly);
    }
}
