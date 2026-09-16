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
package de.cuioss.http.client.result;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.*;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link HttpErrorCategory} enum to verify the simplified essential error codes.
 * Tests only the behavioral method that matters: isRetryable().
 */
class HttpErrorCategoryTest {

    @Test
    void shouldIdentifyRetryableErrors() {
        // Retryable errors - transient conditions
        assertTrue(HttpErrorCategory.NETWORK_ERROR.isRetryable());
        assertTrue(HttpErrorCategory.SERVER_ERROR.isRetryable());

        // Non-retryable errors - permanent conditions
        assertFalse(HttpErrorCategory.CLIENT_ERROR.isRetryable());
        assertFalse(HttpErrorCategory.INVALID_CONTENT.isRetryable());
        assertFalse(HttpErrorCategory.CONFIGURATION_ERROR.isRetryable());
        assertFalse(HttpErrorCategory.INTERRUPTED_ERROR.isRetryable());
    }

    @Test
    void shouldHaveMinimalButSufficientStates() {
        // Verify we have exactly the essential states
        HttpErrorCategory[] allCodes = HttpErrorCategory.values();
        assertEquals(6, allCodes.length, "Should have exactly 6 essential error codes");

        // Verify all expected codes exist
        assertNotNull(HttpErrorCategory.valueOf("NETWORK_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("SERVER_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("CLIENT_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("INVALID_CONTENT"));
        assertNotNull(HttpErrorCategory.valueOf("CONFIGURATION_ERROR"));
        assertNotNull(HttpErrorCategory.valueOf("INTERRUPTED_ERROR"));
    }

    @Test
    void shouldProvideSemanticClarityThroughNaming() {
        // Error types are self-explanatory through enum names
        // No need for additional classification methods
        for (HttpErrorCategory errorCode : HttpErrorCategory.values()) {
            assertNotNull(errorCode);
            assertNotNull(errorCode.name());

            // All enum names should be descriptive (either ending with _ERROR or _CONTENT)
            boolean hasValidSuffix = errorCode.name().endsWith("_ERROR") ||
                    errorCode.name().endsWith("_CONTENT");
            assertTrue(hasValidSuffix,
                    "Error code " + errorCode + " should have descriptive suffix");
        }
    }

    @Test
    void shouldCorrectlyIdentifyRetryableErrors() {
        var retryable = Arrays.stream(HttpErrorCategory.values())
                .filter(HttpErrorCategory::isRetryable)
                .collect(Collectors.toSet());
        assertEquals(Set.of(HttpErrorCategory.NETWORK_ERROR, HttpErrorCategory.SERVER_ERROR), retryable,
                "Only NETWORK_ERROR and SERVER_ERROR should be retryable.");
    }

    @Test
    void shouldCorrectlyIdentifyNonRetryableErrors() {
        var nonRetryable = Arrays.stream(HttpErrorCategory.values())
                .filter(c -> !c.isRetryable())
                .collect(Collectors.toSet());
        assertEquals(Set.of(HttpErrorCategory.CLIENT_ERROR, HttpErrorCategory.INVALID_CONTENT,
                        HttpErrorCategory.CONFIGURATION_ERROR, HttpErrorCategory.INTERRUPTED_ERROR), nonRetryable,
                "CLIENT_ERROR, INVALID_CONTENT, CONFIGURATION_ERROR and INTERRUPTED_ERROR should be non-retryable.");
    }

    @Test
    void fromExceptionShouldMapIOExceptionToNetworkError() {
        assertEquals(HttpErrorCategory.NETWORK_ERROR,
                HttpErrorCategory.fromException(new IOException("timeout")));
        assertEquals(HttpErrorCategory.NETWORK_ERROR,
                HttpErrorCategory.fromException(new SocketTimeoutException()),
                "IOException subclasses are network errors too");
    }

    @Test
    void fromExceptionShouldUnwrapAsyncWrappersAroundIOException() {
        // CompletableFuture pipelines deliver failures wrapped in CompletionException/ExecutionException.
        assertEquals(HttpErrorCategory.NETWORK_ERROR,
                HttpErrorCategory.fromException(new CompletionException(new IOException("connect failed"))),
                "CompletionException-wrapped IOException must be a retryable network error");
        assertEquals(HttpErrorCategory.NETWORK_ERROR,
                HttpErrorCategory.fromException(new ExecutionException(new SocketTimeoutException())),
                "ExecutionException-wrapped IOException must be a retryable network error");
        // Nested wrappers are unwrapped too.
        assertEquals(HttpErrorCategory.NETWORK_ERROR,
                HttpErrorCategory.fromException(
                        new CompletionException(new ExecutionException(new IOException("read timeout")))));
        // A wrapper around a non-IOException stays a configuration error.
        assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                HttpErrorCategory.fromException(new CompletionException(new IllegalStateException("bad state"))));
    }

    @Test
    void fromExceptionShouldMapOtherThrowablesToConfigurationError() {
        assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                HttpErrorCategory.fromException(new IllegalArgumentException("bad request")));
        assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                HttpErrorCategory.fromException(new IllegalStateException("bad state")));
        assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                HttpErrorCategory.fromException(new RuntimeException("other")));
    }

    /**
     * Verifies interruption classification: an {@link InterruptedException} maps to the
     * non-retryable {@link HttpErrorCategory#INTERRUPTED_ERROR} — bare and through the async unwrap
     * loop — and the thread's interrupt flag, which the throw cleared, is restored before
     * {@link HttpErrorCategory#fromException(Throwable)} returns.
     */
    @Nested
    class InterruptionClassification {

        /**
         * Clears the interrupt flag after every test in this class, whether or not that test set
         * it. Restoring the flag is the behaviour under test, so a flag left set would leak into
         * unrelated tests sharing the thread.
         */
        @AfterEach
        void clearInterruptFlag() {
            Thread.interrupted();
        }

        @Test
        void shouldMapInterruptedExceptionToInterruptedError() {
            assertEquals(HttpErrorCategory.INTERRUPTED_ERROR,
                    HttpErrorCategory.fromException(new InterruptedException("cancelled")));
        }

        @Test
        void shouldMapAsyncWrappedInterruptedExceptionToInterruptedError() {
            assertAll("the unwrap loop reaches the interruption through both async wrappers",
                    () -> assertEquals(HttpErrorCategory.INTERRUPTED_ERROR,
                            HttpErrorCategory.fromException(
                                    new CompletionException(new InterruptedException("cancelled"))),
                            "wrapped in CompletionException"),
                    () -> assertEquals(HttpErrorCategory.INTERRUPTED_ERROR,
                            HttpErrorCategory.fromException(
                                    new ExecutionException(new InterruptedException("cancelled"))),
                            "wrapped in ExecutionException"));
        }

        @Test
        void shouldRestoreTheInterruptFlag() {
            assertFalse(Thread.currentThread().isInterrupted(), "precondition: the flag starts clear");

            HttpErrorCategory category =
                    HttpErrorCategory.fromException(new InterruptedException("cancelled"));

            assertEquals(HttpErrorCategory.INTERRUPTED_ERROR, category);
            assertTrue(Thread.currentThread().isInterrupted(),
                    "fromException must restore the flag the throw cleared");
        }

        @Test
        void shouldLeaveTheInterruptFlagAloneForOtherFailures() {
            assertFalse(Thread.currentThread().isInterrupted(), "precondition: the flag starts clear");

            assertEquals(HttpErrorCategory.NETWORK_ERROR,
                    HttpErrorCategory.fromException(new IOException("timeout")));

            assertFalse(Thread.currentThread().isInterrupted(),
                    "a failure that is not an interruption must not touch the flag");
        }

        @Test
        void shouldNotBeRetryable() {
            assertFalse(HttpErrorCategory.INTERRUPTED_ERROR.isRetryable(),
                    "retrying would ignore the cancellation that was asked for");
        }
    }

    /**
     * Verifies the TLS carve-out: the three unconditionally non-transient TLS subtypes are
     * non-retryable configuration errors even though they inherit from {@link IOException}, while
     * the {@link SSLException} base type and the HTTP timeout types stay on the retryable
     * network-error path. {@link SSLHandshakeException} is the conditional one — it is classified
     * by the root of its cause chain, so both outcomes are asserted here.
     */
    @Nested
    class TlsClassification {

        @Test
        void shouldMapCauselessHandshakeFailureToConfigurationError() {
            assertNonRetryableTlsFailure(new SSLHandshakeException("untrusted certificate"));
        }

        @Test
        void shouldMapCertificatePathCausedHandshakeFailureToConfigurationError() {
            SSLHandshakeException untrustedCertificate = new SSLHandshakeException("PKIX path building failed");
            untrustedCertificate.initCause(new CertificateException("no trusted certificate found",
                    new CertPathValidatorException("unable to find valid certification path")));

            assertNonRetryableTlsFailure(untrustedCertificate);
        }

        static Stream<Throwable> transportLevelCauses() {
            return Stream.of(
                    new SocketException("Connection reset"),
                    new EOFException("SSL peer shut down incorrectly"),
                    new SocketTimeoutException("Read timed out"),
                    new HttpConnectTimeoutException("connect timed out"));
        }

        @ParameterizedTest
        @MethodSource("transportLevelCauses")
        void shouldMapTransportCausedHandshakeFailureToNetworkError(Throwable transportCause) {
            SSLHandshakeException cutShort = new SSLHandshakeException("Remote host terminated the handshake");
            cutShort.initCause(transportCause);

            assertAll(transportCause.getClass().getSimpleName()
                            + " cut the handshake short, so retrying may well succeed",
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(cutShort), "bare"),
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(new CompletionException(cutShort)),
                            "wrapped in CompletionException"),
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(new ExecutionException(cutShort)),
                            "wrapped in ExecutionException"),
                    () -> assertTrue(HttpErrorCategory.fromException(cutShort).isRetryable(),
                            "must be retried"));
        }

        @Test
        void shouldFindTheTransportCauseThroughAnIntermediateLink() {
            SSLHandshakeException cutShort = new SSLHandshakeException("Remote host terminated the handshake");
            cutShort.initCause(new SSLException("Connection reset", new SocketException("Connection reset")));

            assertEquals(HttpErrorCategory.NETWORK_ERROR, HttpErrorCategory.fromException(cutShort),
                    "the chain is walked to its root, not only one link deep");
        }

        @Test
        void shouldMapKeyFailureToConfigurationError() {
            assertNonRetryableTlsFailure(new SSLKeyException("bad key material"));
        }

        @Test
        void shouldMapPeerUnverifiedFailureToConfigurationError() {
            assertNonRetryableTlsFailure(new SSLPeerUnverifiedException("peer not verified"));
        }

        @Test
        void shouldMapProtocolFailureToConfigurationError() {
            assertNonRetryableTlsFailure(new SSLProtocolException("protocol violation"));
        }

        @Test
        void shouldKeepBareSslExceptionRetryable() {
            assertAll("Bare SSLException also covers transient conditions",
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(new SSLException("connection reset during handshake"))),
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(
                                    new CompletionException(new SSLException("connection reset")))));
        }

        @Test
        void shouldKeepHttpTimeoutsRetryable() {
            assertAll("HTTP timeouts remain transient network errors",
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(new HttpTimeoutException("request timed out"))),
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(new HttpConnectTimeoutException("connect timed out"))),
                    () -> assertEquals(HttpErrorCategory.NETWORK_ERROR,
                            HttpErrorCategory.fromException(
                                    new CompletionException(new HttpConnectTimeoutException("connect timed out")))));
        }

        /**
         * Asserts the given TLS failure classifies as {@code CONFIGURATION_ERROR} bare and through
         * both async wrappers, exercising the unwrap loop in
         * {@link HttpErrorCategory#fromException(Throwable)}.
         */
        private void assertNonRetryableTlsFailure(SSLException tlsFailure) {
            assertAll(tlsFailure.getClass().getSimpleName() + " is a non-retryable TLS failure",
                    () -> assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                            HttpErrorCategory.fromException(tlsFailure), "bare"),
                    () -> assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                            HttpErrorCategory.fromException(new CompletionException(tlsFailure)),
                            "wrapped in CompletionException"),
                    () -> assertEquals(HttpErrorCategory.CONFIGURATION_ERROR,
                            HttpErrorCategory.fromException(new ExecutionException(tlsFailure)),
                            "wrapped in ExecutionException"),
                    () -> assertFalse(HttpErrorCategory.fromException(tlsFailure).isRetryable(),
                            "must not be retried"));
        }
    }
}