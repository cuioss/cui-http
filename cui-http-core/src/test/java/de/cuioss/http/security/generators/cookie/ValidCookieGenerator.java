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
package de.cuioss.http.security.generators.cookie;

import de.cuioss.http.security.data.Cookie;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.Base64;

/**
 * Generates legitimate Cookie records for testing valid cookie handling.
 *
 * <p>Values cover the whole RFC 6265 {@code cookie-octet} set, including base64-shaped values
 * with {@code +}, {@code /} and {@code =} padding.</p>
 *
 * <p>QI-6: Converted from fixedValues() to dynamic algorithmic generation.</p>
 *
 * This generator produces only valid, legitimate cookie patterns that should be
 * accepted by security validation systems. It complements AttackCookieGenerator
 * which generates malicious patterns.
 * <p>
 * FRAMEWORK COMPLIANT: Uses seed-based generation without call-counter anti-pattern.
 * Reproducibility = f(seed), not f(internal_state).
 */
public class ValidCookieGenerator implements TypedGenerator<Cookie> {

    /**
     * RFC 6265 section 4.1.1:
     * {@code cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E}. Declared from the RFC
     * rather than read from the production character set, so the generator states what is legal
     * independently of what production currently accepts.
     */
    private static final String COOKIE_OCTETS = cookieOctets();

    /**
     * Cookie-octets that are neither a dot nor a hex digit; one of them follows every {@code .}
     * and {@code %} of a cookie-octet value.
     */
    private static final String MARKER_SAFE_FOLLOWERS = "ghijklmnopqrstuvwxyz";

    private final TypedGenerator<Integer> base64ByteCountGen = Generators.integers(1, 24);
    private final TypedGenerator<Integer> byteGen = Generators.integers(0, 255);
    private final TypedGenerator<Integer> cookieOctetLengthGen = Generators.integers(8, 24);

    // QI-6: Dynamic generation components - all seed-based, no internal state
    private final TypedGenerator<Integer> cookieTypeGenerator = Generators.integers(0, 3);
    private final TypedGenerator<Integer> sessionTypeGen = Generators.integers(1, 4);
    private final TypedGenerator<Integer> tokenTypeGen = Generators.integers(1, 4);
    private final TypedGenerator<Integer> contextTypeGen = Generators.integers(1, 4);
    private final TypedGenerator<Integer> domainTypeGen = Generators.integers(1, 3);
    private final TypedGenerator<Integer> pathTypeGen = Generators.integers(1, 4);
    private final TypedGenerator<Boolean> contextSelector = Generators.booleans();
    private final TypedGenerator<Integer> numberValues = Generators.integers(10000, 99999);

    @Override
    public Cookie next() {
        int type = cookieTypeGenerator.next();

        String name = switch (type) {
            case 0 -> generateSessionName();
            case 1 -> generateTokenName();
            case 2 -> generatePreferenceName();
            case 3 -> generateContextName();
            default -> generateSessionName();
        };

        String value = generateLegitimateValue();
        String attributes = generateValidAttributes();

        return new Cookie(name, value, attributes);
    }

    private String generateSessionName() {
        int nameType = Generators.integers(0, 3).next();
        return switch (nameType) {
            case 0 -> "JSESSIONID";
            case 1 -> "session_id";
            case 2 -> generateSessionCategory() + "ID";
            case 3 -> generateSessionCategory() + "_" + generateTokenCategory();
            default -> "JSESSIONID";
        };
    }

    private String generateSessionCategory() {
        return switch (sessionTypeGen.next()) {
            case 1 -> "JSESSION";
            case 2 -> "session";
            case 3 -> "auth";
            case 4 -> "csrf";
            default -> "session";
        };
    }

    private String generateTokenCategory() {
        return switch (tokenTypeGen.next()) {
            case 1 -> "token";
            case 2 -> "id";
            case 3 -> "key";
            case 4 -> "hash";
            default -> "token";
        };
    }

    private String generateTokenName() {
        int nameType = Generators.integers(0, 4).next();
        return switch (nameType) {
            case 0 -> "auth_token";
            case 1 -> "csrf_token";
            case 2 -> "user_id";
            case 3 -> generateContextCategory() + "_" + generateTokenCategory();
            case 4 -> generateSessionCategory() + "_" + generateTokenCategory();
            default -> "auth_token";
        };
    }

    private String generateContextCategory() {
        return switch (contextTypeGen.next()) {
            case 1 -> "user";
            case 2 -> "cart";
            case 3 -> "device";
            case 4 -> "track";
            default -> "user";
        };
    }

    private String generatePreferenceName() {
        int prefType = Generators.integers(0, 3).next();
        return switch (prefType) {
            case 0 -> "language";
            case 1 -> "theme";
            case 2 -> "preferences";
            case 3 -> "settings";
            default -> "language";
        };
    }

    private String generateContextName() {
        String context = generateContextCategory();
        String suffix = generateDataSuffix();
        return context + "_" + suffix;
    }

    private String generateDataSuffix() {
        int suffixType = Generators.integers(0, 3).next();
        return switch (suffixType) {
            case 0 -> "id";
            case 1 -> "data";
            case 2 -> "info";
            case 3 -> "state";
            default -> "id";
        };
    }

    private String generateLegitimateValue() {
        int valueType = Generators.integers(0, 8).next();
        return switch (valueType) {
            case 0 -> generateSessionValue();
            case 1 -> generateBooleanValue();
            case 2 -> generateLocaleValue();
            case 3 -> generateThemeValue();
            case 4 -> generateAlphanumericValue();
            case 5 -> generateTokenValue();
            case 6 -> generateContextValue();
            case 7 -> generateBase64Value();
            case 8 -> generateCookieOctetValue();
            default -> generateSessionValue();
        };
    }

    /**
     * Produces the base64 encoding of 1 to 24 random bytes, the shape of an opaque session or
     * signature value. The standard alphabet carries {@code +} and {@code /}, and a byte count
     * that is not a multiple of three closes the value with one or two {@code =} padding
     * characters - all three are RFC 6265 cookie-octets.
     */
    private String generateBase64Value() {
        byte[] bytes = new byte[base64ByteCountGen.next()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = byteGen.next().byteValue();
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Produces a value drawn from the whole RFC 6265 {@code cookie-octet} set, so that every legal
     * value character is reachable and not only the token alphabet.
     *
     * <p>The value stays legitimate: a {@code .} or a {@code %} is always followed by a character
     * from {@link #MARKER_SAFE_FOLLOWERS}, so the value can form neither a dot-dot traversal
     * segment nor a percent-escape - both are legal cookie-octet sequences, but a value carrying
     * one is what the attack generators emit, not this one.</p>
     */
    private String generateCookieOctetValue() {
        int length = cookieOctetLengthGen.next();
        StringBuilder value = new StringBuilder(length);
        boolean needsSafeFollower = false;
        for (int i = 0; i < length; i++) {
            String alphabet = needsSafeFollower ? MARKER_SAFE_FOLLOWERS : COOKIE_OCTETS;
            char character = alphabet.charAt(Generators.integers(0, alphabet.length() - 1).next());
            value.append(character);
            needsSafeFollower = character == '.' || character == '%';
        }
        return value.toString();
    }

    private static String cookieOctets() {
        StringBuilder octets = new StringBuilder();
        octets.append((char) 0x21);
        appendRange(octets, 0x23, 0x2B);
        appendRange(octets, 0x2D, 0x3A);
        appendRange(octets, 0x3C, 0x5B);
        appendRange(octets, 0x5D, 0x7E);
        return octets.toString();
    }

    private static void appendRange(StringBuilder target, int firstInclusive, int lastInclusive) {
        for (int character = firstInclusive; character <= lastInclusive; character++) {
            target.append((char) character);
        }
    }

    private String generateSessionValue() {
        String prefix = generateContextCategory();
        int number = numberValues.next();
        if (contextSelector.next()) {
            return "session_" + number;
        }
        return prefix + "_" + number;
    }

    private String generateBooleanValue() {
        int boolType = Generators.integers(0, 3).next();
        return switch (boolType) {
            case 0 -> "true";
            case 1 -> "false";
            case 2 -> "yes";
            case 3 -> "no";
            default -> "true";
        };
    }

    private String generateLocaleValue() {
        int localeType = Generators.integers(0, 4).next();
        return switch (localeType) {
            case 0 -> "en_US";
            case 1 -> generateLanguage() + "_" + generateCountry();
            case 2 -> "de_DE";
            case 3 -> "fr_FR";
            case 4 -> "es_ES";
            default -> "en_US";
        };
    }

    private String generateLanguage() {
        int langType = Generators.integers(0, 4).next();
        return switch (langType) {
            case 0 -> "en";
            case 1 -> "de";
            case 2 -> "fr";
            case 3 -> "es";
            case 4 -> "ja";
            default -> "en";
        };
    }

    private String generateCountry() {
        int countryType = Generators.integers(0, 4).next();
        return switch (countryType) {
            case 0 -> "US";
            case 1 -> "DE";
            case 2 -> "FR";
            case 3 -> "ES";
            case 4 -> "JP";
            default -> "US";
        };
    }

    private String generateThemeValue() {
        int themeType = Generators.integers(0, 3).next();
        return switch (themeType) {
            case 0 -> "dark";
            case 1 -> "light";
            case 2 -> "blue";
            case 3 -> "green";
            default -> "dark";
        };
    }

    private String generateAlphanumericValue() {
        int length = Generators.integers(8, 16).next();
        return Generators.letterStrings(length, length + 4).next().toUpperCase();
    }

    private String generateTokenValue() {
        int prefixType = Generators.integers(0, 2).next();
        String prefix = switch (prefixType) {
            case 0 -> "token";
            case 1 -> "device";
            case 2 -> "remember";
            default -> "token";
        };

        int statusType = Generators.integers(0, 2).next();
        String status = switch (statusType) {
            case 0 -> "valid";
            case 1 -> "active";
            case 2 -> "yes";
            default -> "valid";
        };

        return prefix + "_" + status;
    }

    private String generateContextValue() {
        String context = generateContextCategory();
        String suffix = Generators.letterStrings(6, 10).next().toLowerCase();
        return context + "_" + suffix;
    }

    private String generateValidAttributes() {
        int attributeType = Generators.integers(0, 3).next();
        return switch (attributeType) {
            case 0 -> ""; // No attributes
            case 1 -> generateSingleValidAttribute();
            case 2 -> generateMultipleValidAttributes();
            case 3 -> generateSecureAttributes();
            default -> "";
        };
    }

    private String generateSingleValidAttribute() {
        int attrType = Generators.integers(0, 6).next();
        return switch (attrType) {
            case 0 -> "Domain=" + generateLegitimateDomain();
            case 1 -> "Path=" + generateLegitimatePath();
            case 2 -> "Secure";
            case 3 -> "HttpOnly";
            case 4 -> "SameSite=" + generateSameSite();
            case 5 -> "Max-Age=" + Generators.integers(60, 86400).next();
            case 6 -> "Expires=" + generateExpires();
            default -> "Domain=" + generateLegitimateDomain();
        };
    }

    private String generateLegitimateDomain() {
        int domainType = Generators.integers(0, 2).next();
        return switch (domainType) {
            case 0 -> generateDomainCategory() + ".com";
            case 1 -> generateDomainCategory() + ".org";
            case 2 -> generateDomainCategory() + ".net";
            default -> "example.com";
        };
    }

    private String generateDomainCategory() {
        return switch (domainTypeGen.next()) {
            case 1 -> "example";
            case 2 -> "test";
            case 3 -> "demo";
            default -> "example";
        };
    }

    private String generateLegitimatePath() {
        int pathType = Generators.integers(0, 4).next();
        return switch (pathType) {
            case 0 -> "/";
            case 1 -> "/" + generatePathCategory();
            case 2 -> "/" + generatePathCategory() + "/" + generatePathCategory();
            case 3 -> "/" + generatePathCategory() + "/files";
            case 4 -> "/" + generatePathCategory() + "/data";
            default -> "/";
        };
    }

    private String generatePathCategory() {
        return switch (pathTypeGen.next()) {
            case 1 -> "admin";
            case 2 -> "api";
            case 3 -> "user";
            case 4 -> "files";
            default -> "admin";
        };
    }

    private String generateSameSite() {
        int siteType = Generators.integers(0, 2).next();
        return switch (siteType) {
            case 0 -> "Strict";
            case 1 -> "Lax";
            case 2 -> "None";
            default -> "Lax";
        };
    }

    /**
     * Produces a complete RFC 1123 date, the {@code sane-cookie-date} form RFC 6265 requires for
     * {@code Expires}: {@code EEE, dd MMM yyyy HH:mm:ss 'GMT'}. Both arms carry the time-of-day and
     * the {@code GMT} zone, and the day-of-week name matches the calendar date, so the value
     * round-trips through {@link java.time.format.DateTimeFormatter#RFC_1123_DATE_TIME}.
     * <p>
     * The comma-and-space after the day-of-week is required RFC 1123 syntax and is safe to emit:
     * {@code AttributeParser.extractAttributeValue} splits attribute strings on {@code ';'} only and
     * never on {@code ','}, so the comma cannot fragment attribute parsing.
     */
    private String generateExpires() {
        int expiresType = Generators.integers(0, 1).next();
        return switch (expiresType) {
            case 0 -> "Thu, 01 Jan 1970 00:00:00 GMT";
            case 1 -> "Tue, 31 Dec 2999 23:59:59 GMT";
            default -> "Thu, 01 Jan 1970 00:00:00 GMT";
        };
    }

    private String generateMultipleValidAttributes() {
        StringBuilder attrs = new StringBuilder();
        String domain = "Domain=" + generateLegitimateDomain();
        String path = "Path=" + generateLegitimatePath();
        attrs.append(domain).append("; ").append(path);

        if (contextSelector.next()) {
            attrs.append("; Secure");
        }
        if (contextSelector.next()) {
            attrs.append("; HttpOnly");
        }
        if (contextSelector.next()) {
            attrs.append("; SameSite=").append(generateSameSite());
        }

        return attrs.toString();
    }

    private String generateSecureAttributes() {
        return "Domain=" + generateLegitimateDomain() + "; Path=/; Secure; HttpOnly; SameSite=Strict";
    }

    @Override
    public Class<Cookie> getType() {
        return Cookie.class;
    }
}