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
package de.cuioss.http.security.data;

import de.cuioss.http.security.core.ValidationType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Immutable record representing an HTTP request or response body with content, content type, and encoding.
 *
 * <p>This record encapsulates the structure of HTTP message bodies, providing a type-safe way
 * to handle body data in HTTP security validation. It supports various content types and
 * encoding schemes commonly used in HTTP communications.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All fields are final and the record cannot be modified</li>
 *   <li><strong>Type Safety</strong> - Strongly typed representation of HTTP body data</li>
 *   <li><strong>Encoding Awareness</strong> - Explicit handling of content encoding</li>
 *   <li><strong>Content Type Support</strong> - Supports MIME type specification</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // JSON body
 * HTTPBody jsonBody = new HTTPBody(
 *     "{\"userId\": 123, \"name\": \"John\"}",
 *     "application/json",
 *     ""
 * );
 *
 * // Form data
 * HTTPBody formBody = new HTTPBody(
 *     "username=admin&amp;password=secret",
 *     "application/x-www-form-urlencoded",
 *     ""
 * );
 *
 * // Compressed content
 * HTTPBody compressedBody = new HTTPBody(
 *     "...", // compressed content
 *     "text/html",
 *     "gzip"
 * );
 *
 * // Access components
 * String content = body.content();           // The actual content
 * String contentType = body.contentType();   // "application/json"
 * String encoding = body.encoding();         // "gzip"
 *
 * // Check content characteristics
 * boolean isJson = body.isJson();           // true for JSON content
 * boolean hasContent = body.hasContent();   // true if content is not empty
 * boolean isCompressed = body.isCompressed(); // true if encoding is specified
 *
 * // Use in validation
 * validator.validate(body.content(), ValidationType.BODY);
 * </pre>
 *
 * <h3>Content Types</h3>
 * <p>The contentType field should contain a valid MIME type (e.g., "application/json",
 * "text/html", "multipart/form-data"). An empty string indicates no content type is specified.</p>
 *
 * <h3>Encoding</h3>
 * <p>The encoding field specifies content encoding such as "gzip", "deflate", "br" (Brotli),
 * or "" for no encoding. This is distinct from character encoding, which is typically
 * specified in the Content-Type header.</p>
 *
 * <h3>Security Considerations</h3>
 * <p>This record is a simple data container. Security validation should be applied to
 * the content using appropriate validators for {@link ValidationType#BODY}, taking into
 * account the content type and encoding when determining validation strategies.</p>
 *
 * Implements: Task B3 from HTTP verification specification
 *
 * @param content The body content as a string
 * @param contentType The MIME content type (e.g., "application/json", "text/html")
 * @param encoding The content encoding (e.g., "gzip", "deflate", "" for none)
 *
 * @since 1.0
 * @see ValidationType#BODY
 */
public record HTTPBody(@Nullable
String content, @Nullable
String contentType, @Nullable
String encoding) {

    public static HTTPBody of(String content, String contentType) {
        return new HTTPBody(content, contentType, "");
    }

    public static HTTPBody text(String content) {
        return new HTTPBody(content, "text/plain", "");
    }

    public static HTTPBody json(String jsonContent) {
        return new HTTPBody(jsonContent, "application/json", "");
    }

    public static HTTPBody html(String htmlContent) {
        return new HTTPBody(htmlContent, "text/html", "");
    }

    public static HTTPBody form(String formContent) {
        return new HTTPBody(formContent, "application/x-www-form-urlencoded", "");
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasContentType() {
        return contentType != null && !contentType.isEmpty();
    }

    public boolean hasEncoding() {
        return encoding != null && !encoding.isEmpty();
    }

    public boolean isCompressed() {
        return hasEncoding();
    }

    @SuppressWarnings("ConstantConditions")
    public boolean isJson() {
        return hasContentType() && contentType.toLowerCase().contains("json");
    }

    /**
     * Checks if the content type indicates XML content.
     *
     * @return true if the content type contains "xml"
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isXml() {
        return hasContentType() && contentType.toLowerCase().contains("xml");
    }

    /**
     * Checks if the content type indicates HTML content.
     *
     * @return true if the content type contains "html"
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isHtml() {
        return hasContentType() && contentType.toLowerCase().contains("html");
    }

    /**
     * Checks if the content type indicates plain text.
     *
     * @return true if the content type is "text/plain"
     */
    public boolean isPlainText() {
        return hasContentType() && "text/plain".equalsIgnoreCase(contentType);
    }

    /**
     * Checks if the content type indicates form data.
     *
     * @return true if the content type is form-encoded
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isFormData() {
        return hasContentType() &&
                (contentType.toLowerCase().contains("application/x-www-form-urlencoded") ||
                        contentType.toLowerCase().contains("multipart/form-data"));
    }

    /**
     * Checks if the content type indicates binary content.
     *
     * @return true if the content type suggests binary data
     */
    @SuppressWarnings("ConstantConditions")
    public boolean isBinary() {
        return hasContentType() &&
                (contentType.toLowerCase().contains("application/octet-stream") ||
                        contentType.toLowerCase().contains("image/") ||
                        contentType.toLowerCase().contains("video/") ||
                        contentType.toLowerCase().contains("audio/"));
    }

    /**
     * Returns the content length in characters.
     *
     * @return The length of the content string, or 0 if content is null
     */
    public int contentLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * Extracts the charset from the content type if specified.
     *
     * @return The charset name wrapped in Optional, or empty if not specified
     */
    public Optional<String> getCharset() {
        if (!hasContentType()) {
            return Optional.empty();
        }
        return AttributeParser.extractAttributeValue(contentType, "charset");
    }

    /**
     * Returns the content or a default value if content is null.
     *
     * @param defaultContent The default content to return if content is null
     * @return The content or the default
     */
    public String contentOrDefault(String defaultContent) {
        return content != null ? content : defaultContent;
    }

    /**
     * Returns the content type or a default value if content type is null.
     *
     * @param defaultContentType The default content type to return if contentType is null
     * @return The content type or the default
     */
    public String contentTypeOrDefault(String defaultContentType) {
        return contentType != null ? contentType : defaultContentType;
    }

    /**
     * Returns the encoding or a default value if encoding is null.
     *
     * @param defaultEncoding The default encoding to return if encoding is null
     * @return The encoding or the default
     */
    public String encodingOrDefault(String defaultEncoding) {
        return encoding != null ? encoding : defaultEncoding;
    }

    /**
     * Returns a copy of this body with new content.
     *
     * @param newContent The new content
     * @return A new HTTPBody with the specified content and same contentType/encoding
     */
    public HTTPBody withContent(String newContent) {
        return new HTTPBody(newContent, contentType, encoding);
    }

    /**
     * Returns a copy of this body with a new content type.
     *
     * @param newContentType The new content type
     * @return A new HTTPBody with the same content/encoding and specified content type
     */
    public HTTPBody withContentType(String newContentType) {
        return new HTTPBody(content, newContentType, encoding);
    }

    /**
     * Returns a copy of this body with a new encoding.
     *
     * @param newEncoding The new encoding
     * @return A new HTTPBody with the same content/contentType and specified encoding
     */
    public HTTPBody withEncoding(String newEncoding) {
        return new HTTPBody(content, contentType, newEncoding);
    }

    /**
     * Returns a truncated version of the content for safe logging.
     *
     * @param maxLength The maximum length for the truncated content
     * @return The content truncated to the specified length with "..." if truncated
     */
    public String contentTruncated(int maxLength) {
        if (content == null) {
            return "null";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}