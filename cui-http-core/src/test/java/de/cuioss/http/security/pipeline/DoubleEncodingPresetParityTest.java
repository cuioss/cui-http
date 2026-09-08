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
package de.cuioss.http.security.pipeline;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the corrected {@code LENIENT_CONFIGURATION} security callout: choosing the lenient preset
 * gives up <em>no</em> double-encoding or Unicode-fold detection (ADR-0017).
 *
 * <p>Every test here drives a {@link URLPathValidationPipeline} twice over the same input - once
 * with {@link SecurityConfiguration#defaults()} and once with {@link SecurityConfiguration#lenient()}
 * - and asserts the two presets reach the <strong>same</strong> verdict. <em>The parity is the
 * assertion.</em> A test that merely asserted "lenient rejects this" would still pass if the
 * default preset had silently stopped rejecting it, so both halves are always compared to each
 * other rather than each to a hard-coded expectation alone.</p>
 *
 * <h3>Why this is not covered by {@code PostDecodeEnforcementRegressionTest}</h3>
 * <p>{@code rejectsDoublyEncodedDoubleDotBeforeNormalization} sets {@code allowDoubleEncoding(true)}
 * on an <em>otherwise-default</em> configuration. That pins the single flag, which is a different
 * question from whole-preset parity: {@code lenient()} differs from {@code defaults()} in
 * {@code normalizeUnicode} and in its length, count and character rules as well, and it is the
 * combination - specifically {@code normalizeUnicode = false} - that decides which string the
 * caller gets back. That existing test is therefore left untouched.</p>
 *
 * @see <a href="../../../../../../../../doc/adr/0017-DecodingStage_security_gates_must_not_create_a_raw-versus-encoded_verdict_asymmetry.adoc">ADR-0017</a>
 */
@DisplayName("Double-encoding and Unicode-fold parity across the default and lenient presets")
class DoubleEncodingPresetParityTest {

    /**
     * An input whose surviving percent-encoding layer only becomes visible in the
     * <em>canonical</em> form, never in the form {@code lenient()} hands back.
     *
     * <p>{@code %25} decodes to a literal {@code %}; {@code %EF%BC%92} and {@code %EF%BC%A6} decode
     * to U+FF12 FULLWIDTH DIGIT TWO and U+FF26 FULLWIDTH LATIN CAPITAL LETTER F. The decoded string
     * is therefore {@code "/%"} followed by those two fullwidth characters, which carries no ASCII
     * hex digit and so slips past every wire-form and decoded-form check. NFKC then folds it to the
     * live escape {@code "/%2F"} - a still-encoded path separator.</p>
     *
     * <p>This is the input that distinguishes "the gate reads the canonical form" from "the gate
     * reads the value being returned", because under {@code lenient()} those two strings differ and
     * only the first one trips the check.</p>
     */
    private static final String FOLD_ASSEMBLED_ESCAPE = "/%25%EF%BC%92%EF%BC%A6";

    /**
     * The decoded, un-normalised form of {@link #FOLD_ASSEMBLED_ESCAPE}: a literal {@code %}
     * followed by U+FF12 and U+FF26. Written as escapes so the file stays pure ASCII and cannot
     * depend on the compiler's source encoding.
     */
    private static final String FOLD_ASSEMBLED_ESCAPE_DECODED = "/%" + (char) 0xFF12 + (char) 0xFF26;

    /** The pattern {@code DecodingStage} applies to detect a percent-encoding layer that survived. */
    private static final Pattern SURVIVING_ENCODING = Pattern.compile("%[0-9a-fA-F]{2}");

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    private HttpSecurityValidator pipeline(SecurityConfiguration config) {
        return new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * The headline parity case: a doubly encoded traversal sequence is rejected identically by both
     * presets. {@code lenient()} sets {@code allowDoubleEncoding = true}, so before ADR-0017 these
     * inputs were accepted under this preset and rejected under the default one.
     */
    @ParameterizedTest
    @DisplayName("both presets reject a doubly encoded traversal with the identical failure type")
    @ValueSource(strings = {
            "%252e%252e%252f",
            "%252e%252e%255c",
            "%252e%252e/"
    })
    void bothPresetsRejectDoublyEncodedTraversalAlike(String path) {
        UrlSecurityException underDefaults = assertThrows(UrlSecurityException.class,
                () -> pipeline(SecurityConfiguration.defaults()).validate(path),
                "'" + path + "' carries a second encoding layer and must be rejected under defaults()");

        UrlSecurityException underLenient = assertThrows(UrlSecurityException.class,
                () -> pipeline(SecurityConfiguration.lenient()).validate(path),
                "'" + path + "' must be rejected under lenient() too - allowDoubleEncoding=true no "
                        + "longer relaxes the double-encoding gate");

        assertEquals(underDefaults.getFailureType(), underLenient.getFailureType(),
                "the two presets must agree on WHY '" + path + "' is rejected, not merely that it is; "
                        + "a divergence here is the raw-versus-encoded asymmetry ADR-0017 closed");
    }

    /**
     * The negative control. Without it, a pipeline that rejected every input would satisfy the
     * parity assertion above, so this pins that the two presets also agree on acceptance.
     */
    @Test
    @DisplayName("both presets accept the benign control and return it unchanged")
    void bothPresetsAcceptBenignPath() {
        Optional<String> underDefaults = pipeline(SecurityConfiguration.defaults()).validate("/api/v1/resource");
        Optional<String> underLenient = pipeline(SecurityConfiguration.lenient()).validate("/api/v1/resource");

        assertAll("a benign path must survive both presets identically",
                () -> assertEquals("/api/v1/resource", underDefaults.orElseThrow()),
                () -> assertEquals("/api/v1/resource", underLenient.orElseThrow()));
    }

    /**
     * Asserts the <em>operand</em> of the surviving-encoding gate, not merely its outcome.
     *
     * <p>{@link #FOLD_ASSEMBLED_ESCAPE} is rejected under {@code lenient()} even though the string
     * {@code lenient()} would have <em>returned</em> - the un-normalised decoded form - carries no
     * detectable escape at all. That can only be true if the gate inspects the canonical form rather
     * than the value selected for return, which is precisely the property
     * {@code normalizeUnicode = false} would break if the flag still gated detection.</p>
     */
    @Test
    @DisplayName("the surviving-encoding gate reads the canonical form, not the value being returned")
    void gateReadsCanonicalFormNotTheReturnedValue() {
        UrlSecurityException underDefaults = assertThrows(UrlSecurityException.class,
                () -> pipeline(SecurityConfiguration.defaults()).validate(FOLD_ASSEMBLED_ESCAPE));

        UrlSecurityException underLenient = assertThrows(UrlSecurityException.class,
                () -> pipeline(SecurityConfiguration.lenient()).validate(FOLD_ASSEMBLED_ESCAPE),
                "lenient() returns the un-normalised form, so this input is rejected ONLY if the gate "
                        + "inspects the canonical fold instead of the returned value");

        assertAll("both presets must reject the fold-assembled escape for the same reason",
                () -> assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, underDefaults.getFailureType()),
                () -> assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, underLenient.getFailureType()),
                () -> assertEquals(underDefaults.getFailureType(), underLenient.getFailureType()));
    }

    /**
     * Pins the premise the test above rests on: the two candidate operands genuinely differ, and
     * only the canonical one carries a detectable escape. Without this, {@code lenient()} rejecting
     * the input would be consistent with the gate reading either string, and the operand assertion
     * would prove nothing.
     */
    @Test
    @DisplayName("premise: only the canonical fold carries a detectable escape, not the returned form")
    void onlyTheCanonicalFoldCarriesADetectableEscape() {
        String canonical = Normalizer.normalize(FOLD_ASSEMBLED_ESCAPE_DECODED, Normalizer.Form.NFKC);

        assertAll("the returned form and the canonical form must diverge for the operand test to bite",
                () -> assertEquals("/%2F", canonical,
                        "NFKC folds the fullwidth digits into a live percent escape"),
                () -> assertFalse(SURVIVING_ENCODING.matcher(FOLD_ASSEMBLED_ESCAPE_DECODED).find(),
                        "the un-normalised form lenient() returns carries no ASCII hex, so a gate "
                                + "reading it would find nothing"),
                () -> assertTrue(SURVIVING_ENCODING.matcher(canonical).find(),
                        "the canonical form does carry a surviving escape"));
    }
}
