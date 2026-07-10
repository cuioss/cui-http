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
package de.cuioss.http.forwarded;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages for the {@code de.cuioss.http.forwarded} package.
 * All messages follow the format: HTTP-[identifier]: [message].
 *
 * <p>Identifier range 120-129 is reserved for this package (WARN), distinct from the
 * {@code de.cuioss.http.client} ranges in {@code HttpLogMessages}.</p>
 *
 * @since 1.0
 */
@UtilityClass
public final class ForwardedLogMessages {

    private static final String PREFIX = "HTTP";

    /**
     * Warning-level messages for rejected or malformed forwarded-header values.
     */
    @UtilityClass
    public static final class WARN {

        public static final LogRecord CONTEXT_PATH_CONTROL_CHARACTERS_REJECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(120)
                .template("Rejecting proxy context path with control characters: %s")
                .build();

        public static final LogRecord CONTEXT_PATH_PROTOCOL_RELATIVE_REJECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(121)
                .template("Rejecting proxy context path to prevent protocol-relative URL injection: %s")
                .build();

        public static final LogRecord FORWARDED_VALUE_SANITIZATION_REJECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(122)
                .template("Rejecting forwarded header %s: value failed security sanitization: %s")
                .build();

        public static final LogRecord CLIENT_IP_ENTRY_UNPARSEABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(123)
                .template("Ignoring client IP: forwarded chain carries an unparseable entry: %s")
                .build();
    }
}
