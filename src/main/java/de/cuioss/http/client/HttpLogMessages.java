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
    }

}