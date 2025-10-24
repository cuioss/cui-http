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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Type-safe content types (MIME types) with charset support.
 * <p>
 * This enum provides commonly used MIME types with appropriate default charsets.
 * Text-based types (JSON, XML, plain text, HTML) include UTF-8 charset, while binary
 * types (images, PDFs, ZIP files) have no charset.
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Get header value with charset</h3>
 * <pre>{@code
 * ContentType json = ContentType.APPLICATION_JSON;
 * String headerValue = json.toHeaderValue();
 * // Returns: "application/json; charset=UTF-8"
 * }</pre>
 *
 * <h3>Example 2: Binary type without charset</h3>
 * <pre>{@code
 * ContentType pdf = ContentType.APPLICATION_PDF;
 * String headerValue = pdf.toHeaderValue();
 * // Returns: "application/pdf" (no charset)
 * }</pre>
 *
 * <h3>Example 3: Check if type has charset</h3>
 * <pre>{@code
 * if (ContentType.TEXT_PLAIN.defaultCharset().isPresent()) {
 *     Charset charset = ContentType.TEXT_PLAIN.defaultCharset().get();
 *     // charset is UTF-8
 * }
 * }</pre>
 *
 * @since 1.0
 */
public enum ContentType {

    /** JSON content type with UTF-8 charset */
    APPLICATION_JSON("application/json", StandardCharsets.UTF_8),

    /** XML content type with UTF-8 charset */
    APPLICATION_XML("application/xml", StandardCharsets.UTF_8),

    /** Plain text with UTF-8 charset */
    TEXT_PLAIN("text/plain", StandardCharsets.UTF_8),

    /** HTML content with UTF-8 charset */
    TEXT_HTML("text/html", StandardCharsets.UTF_8),

    /** XML text with UTF-8 charset */
    TEXT_XML("text/xml", StandardCharsets.UTF_8),

    /** CSV text with UTF-8 charset */
    TEXT_CSV("text/csv", StandardCharsets.UTF_8),

    /** URL-encoded form data with UTF-8 charset */
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded", StandardCharsets.UTF_8),

    /** Multipart form data (boundary specified separately, no charset) */
    MULTIPART_FORM_DATA("multipart/form-data", null),

    /** Binary octet stream (no charset) */
    APPLICATION_OCTET_STREAM("application/octet-stream", null),

    /** PDF document (no charset) */
    APPLICATION_PDF("application/pdf", null),

    /** ZIP archive (no charset) */
    APPLICATION_ZIP("application/zip", null),

    /** PNG image (no charset) */
    IMAGE_PNG("image/png", null),

    /** JPEG image (no charset) */
    IMAGE_JPEG("image/jpeg", null),

    /** GIF image (no charset) */
    IMAGE_GIF("image/gif", null),

    /** SVG image with UTF-8 charset (text-based format) */
    IMAGE_SVG("image/svg+xml", StandardCharsets.UTF_8);

    private final String mediaType;
    private final Charset defaultCharset;

    /**
     * Constructs a ContentType with the specified media type and charset.
     *
     * @param mediaType     the MIME type (e.g., "application/json")
     * @param defaultCharset the default charset, or null for binary types
     */
    ContentType(String mediaType, Charset defaultCharset) {
        this.mediaType = mediaType;
        this.defaultCharset = defaultCharset;
    }

    /**
     * Returns the media type string.
     *
     * @return the MIME type (e.g., "application/json")
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Returns the default charset for this content type.
     * <p>
     * Text-based types return UTF-8, binary types return empty Optional.
     * </p>
     *
     * @return Optional containing the charset, or empty for binary types
     */
    public Optional<Charset> defaultCharset() {
        return Optional.ofNullable(defaultCharset);
    }

    /**
     * Returns the complete Content-Type header value.
     * <p>
     * For text-based types, includes charset parameter (e.g., "application/json; charset=UTF-8").
     * For binary types, returns only the media type (e.g., "application/pdf").
     * </p>
     *
     * @return the complete Content-Type header value
     */
    public String toHeaderValue() {
        if (defaultCharset != null) {
            return mediaType + "; charset=" + defaultCharset.name();
        }
        return mediaType;
    }
}
