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
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the collection-level count validator (F-12).
 */
@DisplayName("RequestCollectionValidator (F-12)")
class RequestCollectionValidatorTest {

    private SecurityEventCounter eventCounter;
    private RequestCollectionValidator validator;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        validator = new RequestCollectionValidator(SecurityConfiguration.defaults(), eventCounter);
    }

    private static Map<String, String> mapOfSize(int size) {
        return IntStream.range(0, size).boxed()
                .collect(Collectors.toMap(i -> "k" + i, i -> "v" + i));
    }

    @Test
    @DisplayName("Counts within the default limits pass")
    void shouldAcceptWithinLimits() {
        assertDoesNotThrow(() -> validator.validateParameters(mapOfSize(SecurityDefaults.MAX_PARAMETER_COUNT_DEFAULT)));
        assertDoesNotThrow(() -> validator.validateHeaders(mapOfSize(SecurityDefaults.MAX_HEADER_COUNT_DEFAULT)));
        assertDoesNotThrow(() -> validator.validateCookies(
                IntStream.range(0, SecurityDefaults.MAX_COOKIE_COUNT_DEFAULT).boxed().toList()));
        assertEquals(0, eventCounter.getTotalCount());
    }

    @Test
    @DisplayName("Parameter count over the limit is rejected and recorded")
    void shouldRejectTooManyParameters() {
        Map<String, String> tooMany = mapOfSize(SecurityDefaults.MAX_PARAMETER_COUNT_DEFAULT + 1);
        var exception = assertThrows(UrlSecurityException.class,
                () -> validator.validateParameters(tooMany));
        assertEquals(UrlSecurityFailureType.TOO_MANY_ELEMENTS, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("parameter"));
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.TOO_MANY_ELEMENTS));
    }

    @Test
    @DisplayName("Header count over the limit is rejected")
    void shouldRejectTooManyHeaders() {
        var exception = assertThrows(UrlSecurityException.class,
                () -> validator.validateHeaderCount(SecurityDefaults.MAX_HEADER_COUNT_DEFAULT + 1));
        assertEquals(UrlSecurityFailureType.TOO_MANY_ELEMENTS, exception.getFailureType());
    }

    @Test
    @DisplayName("Cookie count over the limit is rejected")
    void shouldRejectTooManyCookies() {
        List<Integer> tooMany = IntStream.range(0, SecurityDefaults.MAX_COOKIE_COUNT_DEFAULT + 1).boxed().toList();
        var exception = assertThrows(UrlSecurityException.class,
                () -> validator.validateCookies(tooMany));
        assertEquals(UrlSecurityFailureType.TOO_MANY_ELEMENTS, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("cookie"));
    }

    @Test
    @DisplayName("Strict preset enforces tighter counts than lenient")
    void shouldHonorPresetLimits() {
        RequestCollectionValidator strict =
                new RequestCollectionValidator(SecurityConfiguration.strict(), eventCounter);
        RequestCollectionValidator lenient =
                new RequestCollectionValidator(SecurityConfiguration.lenient(), new SecurityEventCounter());

        // Strict cookie limit is 10; lenient accepts 50.
        assertThrows(UrlSecurityException.class,
                () -> strict.validateCookieCount(SecurityDefaults.MAX_COOKIE_COUNT_STRICT + 1));
        assertDoesNotThrow(() -> lenient.validateCookieCount(SecurityDefaults.MAX_COOKIE_COUNT_STRICT + 1));
    }

    @Test
    @DisplayName("Custom builder count is honored")
    void shouldHonorCustomLimit() {
        RequestCollectionValidator custom = new RequestCollectionValidator(
                SecurityConfiguration.builder().maxParameterCount(2).build(), eventCounter);
        assertDoesNotThrow(() -> custom.validateParameterCount(2));
        assertThrows(UrlSecurityException.class, () -> custom.validateParameterCount(3));
    }

    @Test
    @DisplayName("Null arguments are rejected")
    void shouldRejectNulls() {
        SecurityConfiguration defaults = SecurityConfiguration.defaults();
        assertThrows(NullPointerException.class,
                () -> new RequestCollectionValidator(null, eventCounter));
        assertThrows(NullPointerException.class,
                () -> new RequestCollectionValidator(defaults, null));
        assertThrows(NullPointerException.class, () -> validator.validateParameters(null));
        assertThrows(NullPointerException.class, () -> validator.validateHeaders(null));
        assertThrows(NullPointerException.class, () -> validator.validateCookies((List<?>) null));
    }
}
