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
package de.cuioss.http.client;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Provides logging messages for the de.cuioss.tools.net.http package.
 * All messages follow the format: HTTP-[identifier]: [message]
 * <p>
 * This separate LogMessages class is specific to the HTTP utilities package
 * and complements the main JWTValidationLogMessages for the module.
 *
 * @since 1.0
 */
@UtilityClass
public final class HttpLogMessages {

    private static final String PREFIX = "HTTP";

    /**
     * Contains informational log messages for successful operations or status updates.
     */
    @UtilityClass
    public static final class INFO {

        public static final LogRecord RETRY_OPERATION_SUCCEEDED_AFTER_ATTEMPTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Operation '%s' succeeded on attempt %s/%s")
                .build();
    }

    /**
     * Contains warning-level log messages for potential issues that don't prevent
     * normal operation but may indicate problems.
     */
    @UtilityClass
    public static final class WARN {

        public static final LogRecord CONTENT_CONVERSION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Content conversion failed for response from %s")
                .build();

        public static final LogRecord HTTP_STATUS_WARNING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("HTTP %s (%s) from %s")
                .build();

        public static final LogRecord HTTP_FETCH_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Failed to fetch HTTP content from %s")
                .build();

        /**
         * Logged when thread is interrupted while fetching HTTP content.
         * Note: Not tested due to complexity of reliably triggering InterruptedException in unit tests.
         * Used in: ResilientHttpHandler.loadInternal (catch InterruptedException)
         */
        public static final LogRecord HTTP_FETCH_INTERRUPTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Interrupted while fetching HTTP content from %s")
                .build();

        public static final LogRecord RETRY_MAX_ATTEMPTS_REACHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(104)
                .template("Operation '%s' failed after %s attempts. Final exception: %s")
                .build();

        public static final LogRecord RETRY_OPERATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Retry operation '%s' failed after %s attempts in %sms")
                .build();

        public static final LogRecord HTTP_PING_IO_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(106)
                .template("IO error while pinging URI %s: %s")
                .build();

        /**
         * Logged when thread is interrupted while pinging a URI.
         * Note: Not tested due to complexity of reliably triggering InterruptedException in unit tests.
         * Used in: HttpHandler.pingWithMethod (catch InterruptedException)
         */
        public static final LogRecord HTTP_PING_INTERRUPTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(107)
                .template("Interrupted while pinging URI %s: %s")
                .build();

        public static final LogRecord SSL_INSECURE_PROTOCOL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(109)
                .template("Provided SSL context uses insecure protocol: %s. Creating a secure context instead.")
                .build();

        public static final LogRecord RETRY_SKIPPED_NON_IDEMPOTENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("Skipping retry for non-idempotent method: %s (idempotentOnly=true)")
                .build();

        public static final LogRecord REQUEST_FAILED_MAX_ATTEMPTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("%s request failed after %s attempts")
                .build();

        public static final LogRecord REQUEST_RETRY_AFTER_FAILURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("%s request failed on attempt %s, retrying after %sms")
                .build();

        public static final LogRecord RESPONSE_CONVERSION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Response conversion failed for status %s")
                .build();

        public static final LogRecord NETWORK_ERROR_DURING_REQUEST = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(114)
                .template("Network error during %s request: %s")
                .build();
    }

    /**
     * Contains error-level log messages for failures that prevent normal operation.
     */
    @UtilityClass
    public static final class ERROR {

        public static final LogRecord CONFIGURATION_ERROR_DURING_REQUEST = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Configuration error during %s request: %s")
                .build();

        public static final LogRecord REQUEST_BUILD_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("Failed to build HTTP request for %s: %s")
                .build();
    }

}