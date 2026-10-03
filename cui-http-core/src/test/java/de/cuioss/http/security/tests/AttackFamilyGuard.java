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
package de.cuioss.http.security.tests;

import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Family filter for a parameterized test that draws from a generator mixing several attack
 * families but asserts the exact verdict of one of them.
 *
 * <p>The test opens with {@code if (!GUARD.admits(sample)) return;}. That early return is only
 * sound while the filter still admits samples: if the generator ever stops producing the family,
 * the filter would swallow every sample and the test would pass while asserting nothing.
 * {@link #assertAllAdmittedSamples(AttackFamilyGuard...)}, called from an {@code @AfterAll}
 * method, turns that silent degradation into a failure.</p>
 *
 * <p>A guard is held in a {@code static final} field, because JUnit creates one test instance per
 * invocation and the counters must span all invocations of the method.</p>
 */
final class AttackFamilyGuard {

    private final String testName;
    private final Predicate<String> family;
    private final AtomicInteger samples = new AtomicInteger();
    private final AtomicInteger admitted = new AtomicInteger();

    /**
     * @param testName the guarded test method, named in the failure message
     * @param family the structural fingerprint of the family the test asserts on
     */
    AttackFamilyGuard(String testName, Predicate<String> family) {
        this.testName = testName;
        this.family = family;
    }

    /**
     * Counts the sample and reports whether it belongs to the guarded family.
     *
     * @param sample a generated payload
     * @return {@code true} when the test must assert on {@code sample}
     */
    boolean admits(String sample) {
        samples.incrementAndGet();
        if (!family.test(sample)) {
            return false;
        }
        admitted.incrementAndGet();
        return true;
    }

    /**
     * Asserts that every guard whose test ran admitted at least one sample. A guard that saw no
     * sample at all is skipped, which keeps a single-method IDE run from failing on the sibling
     * methods it never executed.
     *
     * @param guards the guards of one test class
     */
    static void assertAllAdmittedSamples(AttackFamilyGuard... guards) {
        assertAll("Guarded parameterized tests must not degrade into silent no-ops",
                Arrays.stream(guards).map(guard -> (Executable) guard::assertAdmittedSamples));
    }

    private void assertAdmittedSamples() {
        if (samples.get() == 0) {
            return;
        }
        assertTrue(admitted.get() > 0,
                () -> testName + " saw " + samples.get() + " generated samples but its family guard "
                        + "admitted none — the test asserted nothing this run");
    }
}
