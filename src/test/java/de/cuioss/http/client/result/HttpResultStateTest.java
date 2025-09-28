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
package de.cuioss.http.client.result;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.cuioss.http.client.result.HttpResultState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpResultState} API constants and behavior.
 */
class HttpResultStateTest {

    @Test
    void cacheStatesContainsCorrectStates() {
        assertEquals(CACHE_STATES, Set.of(CACHED, STALE));
        assertFalse(CACHE_STATES.contains(FRESH));
        assertFalse(CACHE_STATES.contains(RECOVERED));
        assertFalse(CACHE_STATES.contains(ERROR));
    }

    @Test
    void successStatesContainsCorrectStates() {
        assertEquals(SUCCESS_STATES, Set.of(FRESH, CACHED, RECOVERED));
        assertFalse(SUCCESS_STATES.contains(STALE));
        assertFalse(SUCCESS_STATES.contains(ERROR));
    }

    @Test
    void degradedStatesContainsCorrectStates() {
        assertEquals(DEGRADED_STATES, Set.of(STALE, RECOVERED));
        assertFalse(DEGRADED_STATES.contains(FRESH));
        assertFalse(DEGRADED_STATES.contains(CACHED));
        assertFalse(DEGRADED_STATES.contains(ERROR));
    }

    @Test
    void mustBeHandledStatesContainsCorrectStates() {
        assertEquals(MUST_BE_HANDLED, Set.of(ERROR, STALE));
        assertFalse(MUST_BE_HANDLED.contains(FRESH));
        assertFalse(MUST_BE_HANDLED.contains(CACHED));
        assertFalse(MUST_BE_HANDLED.contains(RECOVERED));
    }

    @Test
    void cacheStatesIsImmutable() {
        Set<HttpResultState> cacheStates = CACHE_STATES;
        assertInstanceOf(Set.class, cacheStates);

        assertThrows(UnsupportedOperationException.class, () ->
                cacheStates.add(FRESH));
    }

    @Test
    void successStatesIsImmutable() {
        Set<HttpResultState> successStates = SUCCESS_STATES;
        assertInstanceOf(Set.class, successStates);

        assertThrows(UnsupportedOperationException.class, () ->
                successStates.add(ERROR));
    }

    @Test
    void degradedStatesIsImmutable() {
        Set<HttpResultState> degradedStates = DEGRADED_STATES;
        assertInstanceOf(Set.class, degradedStates);

        assertThrows(UnsupportedOperationException.class, () ->
                degradedStates.add(FRESH));
    }

    @Test
    void mustBeHandledStatesIsImmutable() {
        Set<HttpResultState> mustBeHandledStates = MUST_BE_HANDLED;
        assertInstanceOf(Set.class, mustBeHandledStates);

        assertThrows(UnsupportedOperationException.class, () ->
                mustBeHandledStates.add(CACHED));
    }

    @Test
    void stateCollectionsHaveCorrectSemantics() {
        // FRESH should be successful but not cached or degraded
        assertTrue(SUCCESS_STATES.contains(FRESH));
        assertFalse(CACHE_STATES.contains(FRESH));
        assertFalse(DEGRADED_STATES.contains(FRESH));
        assertFalse(MUST_BE_HANDLED.contains(FRESH));

        // CACHED should be successful and cached but not degraded
        assertTrue(SUCCESS_STATES.contains(CACHED));
        assertTrue(CACHE_STATES.contains(CACHED));
        assertFalse(DEGRADED_STATES.contains(CACHED));
        assertFalse(MUST_BE_HANDLED.contains(CACHED));

        // STALE should be cached and degraded and must be handled
        assertFalse(SUCCESS_STATES.contains(STALE));
        assertTrue(CACHE_STATES.contains(STALE));
        assertTrue(DEGRADED_STATES.contains(STALE));
        assertTrue(MUST_BE_HANDLED.contains(STALE));

        // RECOVERED should be successful and degraded but not cached
        assertTrue(SUCCESS_STATES.contains(RECOVERED));
        assertFalse(CACHE_STATES.contains(RECOVERED));
        assertTrue(DEGRADED_STATES.contains(RECOVERED));
        assertFalse(MUST_BE_HANDLED.contains(RECOVERED));

        // ERROR should not be in any positive states but must be handled
        assertFalse(SUCCESS_STATES.contains(ERROR));
        assertFalse(CACHE_STATES.contains(ERROR));
        assertFalse(DEGRADED_STATES.contains(ERROR));
        assertTrue(MUST_BE_HANDLED.contains(ERROR));
    }

    @Test
    void allStatesAreCategorized() {
        // Every enum value should appear in at least one category
        for (HttpResultState state : HttpResultState.values()) {
            boolean isInAtLeastOneCategory = SUCCESS_STATES.contains(state)
                    || CACHE_STATES.contains(state)
                    || DEGRADED_STATES.contains(state)
                    || MUST_BE_HANDLED.contains(state);

            assertTrue(isInAtLeastOneCategory,
                    "State " + state + " should be in at least one category");
        }
    }
}