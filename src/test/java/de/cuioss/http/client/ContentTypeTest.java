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

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContentType} enum.
 */
class ContentTypeTest {

    @Test
    void applicationJsonHasUtf8Charset() {
        ContentType json = ContentType.APPLICATION_JSON;

        assertEquals("application/json", json.mediaType());
        assertTrue(json.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, json.defaultCharset().get());
        assertEquals("application/json; charset=UTF-8", json.toHeaderValue());
    }

    @Test
    void applicationXmlHasUtf8Charset() {
        ContentType xml = ContentType.APPLICATION_XML;

        assertEquals("application/xml", xml.mediaType());
        assertTrue(xml.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, xml.defaultCharset().get());
        assertEquals("application/xml; charset=UTF-8", xml.toHeaderValue());
    }

    @Test
    void textPlainHasUtf8Charset() {
        ContentType text = ContentType.TEXT_PLAIN;

        assertEquals("text/plain", text.mediaType());
        assertTrue(text.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, text.defaultCharset().get());
        assertEquals("text/plain; charset=UTF-8", text.toHeaderValue());
    }

    @Test
    void textHtmlHasUtf8Charset() {
        ContentType html = ContentType.TEXT_HTML;

        assertEquals("text/html", html.mediaType());
        assertTrue(html.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, html.defaultCharset().get());
        assertEquals("text/html; charset=UTF-8", html.toHeaderValue());
    }

    @Test
    void textXmlHasUtf8Charset() {
        ContentType textXml = ContentType.TEXT_XML;

        assertEquals("text/xml", textXml.mediaType());
        assertTrue(textXml.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, textXml.defaultCharset().get());
        assertEquals("text/xml; charset=UTF-8", textXml.toHeaderValue());
    }

    @Test
    void textCsvHasUtf8Charset() {
        ContentType csv = ContentType.TEXT_CSV;

        assertEquals("text/csv", csv.mediaType());
        assertTrue(csv.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, csv.defaultCharset().get());
        assertEquals("text/csv; charset=UTF-8", csv.toHeaderValue());
    }

    @Test
    void formUrlencodedHasUtf8Charset() {
        ContentType form = ContentType.APPLICATION_FORM_URLENCODED;

        assertEquals("application/x-www-form-urlencoded", form.mediaType());
        assertTrue(form.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, form.defaultCharset().get());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", form.toHeaderValue());
    }

    @Test
    void imageSvgHasUtf8Charset() {
        // SVG is text-based XML format, should have charset
        ContentType svg = ContentType.IMAGE_SVG;

        assertEquals("image/svg+xml", svg.mediaType());
        assertTrue(svg.defaultCharset().isPresent());
        assertEquals(StandardCharsets.UTF_8, svg.defaultCharset().get());
        assertEquals("image/svg+xml; charset=UTF-8", svg.toHeaderValue());
    }

    @Test
    void multipartFormDataHasNoCharset() {
        ContentType multipart = ContentType.MULTIPART_FORM_DATA;

        assertEquals("multipart/form-data", multipart.mediaType());
        assertFalse(multipart.defaultCharset().isPresent());
        assertEquals("multipart/form-data", multipart.toHeaderValue());
    }

    @Test
    void applicationOctetStreamHasNoCharset() {
        ContentType binary = ContentType.APPLICATION_OCTET_STREAM;

        assertEquals("application/octet-stream", binary.mediaType());
        assertFalse(binary.defaultCharset().isPresent());
        assertEquals("application/octet-stream", binary.toHeaderValue());
    }

    @Test
    void applicationPdfHasNoCharset() {
        ContentType pdf = ContentType.APPLICATION_PDF;

        assertEquals("application/pdf", pdf.mediaType());
        assertFalse(pdf.defaultCharset().isPresent());
        assertEquals("application/pdf", pdf.toHeaderValue());
    }

    @Test
    void applicationZipHasNoCharset() {
        ContentType zip = ContentType.APPLICATION_ZIP;

        assertEquals("application/zip", zip.mediaType());
        assertFalse(zip.defaultCharset().isPresent());
        assertEquals("application/zip", zip.toHeaderValue());
    }

    @Test
    void imagePngHasNoCharset() {
        ContentType png = ContentType.IMAGE_PNG;

        assertEquals("image/png", png.mediaType());
        assertFalse(png.defaultCharset().isPresent());
        assertEquals("image/png", png.toHeaderValue());
    }

    @Test
    void imageJpegHasNoCharset() {
        ContentType jpeg = ContentType.IMAGE_JPEG;

        assertEquals("image/jpeg", jpeg.mediaType());
        assertFalse(jpeg.defaultCharset().isPresent());
        assertEquals("image/jpeg", jpeg.toHeaderValue());
    }

    @Test
    void imageGifHasNoCharset() {
        ContentType gif = ContentType.IMAGE_GIF;

        assertEquals("image/gif", gif.mediaType());
        assertFalse(gif.defaultCharset().isPresent());
        assertEquals("image/gif", gif.toHeaderValue());
    }

    @Test
    void toHeaderValueIncludesCharsetForTextTypes() {
        // Verify that all text-based types include charset parameter
        String jsonHeader = ContentType.APPLICATION_JSON.toHeaderValue();
        assertTrue(jsonHeader.contains("charset="));
        assertTrue(jsonHeader.endsWith("UTF-8"));

        String xmlHeader = ContentType.APPLICATION_XML.toHeaderValue();
        assertTrue(xmlHeader.contains("charset="));
        assertTrue(xmlHeader.endsWith("UTF-8"));

        String plainHeader = ContentType.TEXT_PLAIN.toHeaderValue();
        assertTrue(plainHeader.contains("charset="));
        assertTrue(plainHeader.endsWith("UTF-8"));

        String htmlHeader = ContentType.TEXT_HTML.toHeaderValue();
        assertTrue(htmlHeader.contains("charset="));
        assertTrue(htmlHeader.endsWith("UTF-8"));
    }

    @Test
    void toHeaderValueExcludesCharsetForBinaryTypes() {
        // Verify that binary types do NOT include charset parameter
        String pdfHeader = ContentType.APPLICATION_PDF.toHeaderValue();
        assertFalse(pdfHeader.contains("charset="));
        assertEquals("application/pdf", pdfHeader);

        String pngHeader = ContentType.IMAGE_PNG.toHeaderValue();
        assertFalse(pngHeader.contains("charset="));
        assertEquals("image/png", pngHeader);

        String zipHeader = ContentType.APPLICATION_ZIP.toHeaderValue();
        assertFalse(zipHeader.contains("charset="));
        assertEquals("application/zip", zipHeader);

        String octetHeader = ContentType.APPLICATION_OCTET_STREAM.toHeaderValue();
        assertFalse(octetHeader.contains("charset="));
        assertEquals("application/octet-stream", octetHeader);
    }

    @Test
    void defaultCharsetReturnsEmptyForBinaryTypes() {
        // Verify Optional.empty() for all binary types
        assertFalse(ContentType.APPLICATION_PDF.defaultCharset().isPresent());
        assertFalse(ContentType.APPLICATION_ZIP.defaultCharset().isPresent());
        assertFalse(ContentType.APPLICATION_OCTET_STREAM.defaultCharset().isPresent());
        assertFalse(ContentType.IMAGE_PNG.defaultCharset().isPresent());
        assertFalse(ContentType.IMAGE_JPEG.defaultCharset().isPresent());
        assertFalse(ContentType.IMAGE_GIF.defaultCharset().isPresent());
        assertFalse(ContentType.MULTIPART_FORM_DATA.defaultCharset().isPresent());
    }

    @Test
    void defaultCharsetReturnsPresentForTextTypes() {
        // Verify Optional with UTF-8 for all text types
        assertTrue(ContentType.APPLICATION_JSON.defaultCharset().isPresent());
        assertTrue(ContentType.APPLICATION_XML.defaultCharset().isPresent());
        assertTrue(ContentType.TEXT_PLAIN.defaultCharset().isPresent());
        assertTrue(ContentType.TEXT_HTML.defaultCharset().isPresent());
        assertTrue(ContentType.TEXT_XML.defaultCharset().isPresent());
        assertTrue(ContentType.TEXT_CSV.defaultCharset().isPresent());
        assertTrue(ContentType.APPLICATION_FORM_URLENCODED.defaultCharset().isPresent());
        assertTrue(ContentType.IMAGE_SVG.defaultCharset().isPresent());
    }

    @Test
    void allTextTypesUseUtf8() {
        // Verify all text types use UTF-8 as default charset
        assertEquals(StandardCharsets.UTF_8, ContentType.APPLICATION_JSON.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.APPLICATION_XML.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.TEXT_PLAIN.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.TEXT_HTML.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.TEXT_XML.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.TEXT_CSV.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.APPLICATION_FORM_URLENCODED.defaultCharset().get());
        assertEquals(StandardCharsets.UTF_8, ContentType.IMAGE_SVG.defaultCharset().get());
    }

    @Test
    void mediaTypeReturnsCorrectValue() {
        // Verify mediaType() returns the correct string for various types
        assertEquals("application/json", ContentType.APPLICATION_JSON.mediaType());
        assertEquals("application/xml", ContentType.APPLICATION_XML.mediaType());
        assertEquals("text/plain", ContentType.TEXT_PLAIN.mediaType());
        assertEquals("text/html", ContentType.TEXT_HTML.mediaType());
        assertEquals("text/xml", ContentType.TEXT_XML.mediaType());
        assertEquals("text/csv", ContentType.TEXT_CSV.mediaType());
        assertEquals("application/x-www-form-urlencoded", ContentType.APPLICATION_FORM_URLENCODED.mediaType());
        assertEquals("multipart/form-data", ContentType.MULTIPART_FORM_DATA.mediaType());
        assertEquals("application/octet-stream", ContentType.APPLICATION_OCTET_STREAM.mediaType());
        assertEquals("application/pdf", ContentType.APPLICATION_PDF.mediaType());
        assertEquals("application/zip", ContentType.APPLICATION_ZIP.mediaType());
        assertEquals("image/png", ContentType.IMAGE_PNG.mediaType());
        assertEquals("image/jpeg", ContentType.IMAGE_JPEG.mediaType());
        assertEquals("image/gif", ContentType.IMAGE_GIF.mediaType());
        assertEquals("image/svg+xml", ContentType.IMAGE_SVG.mediaType());
    }

    @Test
    void enumValuesExist() {
        // Verify all expected enum constants exist
        assertNotNull(ContentType.APPLICATION_JSON);
        assertNotNull(ContentType.APPLICATION_XML);
        assertNotNull(ContentType.TEXT_PLAIN);
        assertNotNull(ContentType.TEXT_HTML);
        assertNotNull(ContentType.TEXT_XML);
        assertNotNull(ContentType.TEXT_CSV);
        assertNotNull(ContentType.APPLICATION_FORM_URLENCODED);
        assertNotNull(ContentType.MULTIPART_FORM_DATA);
        assertNotNull(ContentType.APPLICATION_OCTET_STREAM);
        assertNotNull(ContentType.APPLICATION_PDF);
        assertNotNull(ContentType.APPLICATION_ZIP);
        assertNotNull(ContentType.IMAGE_PNG);
        assertNotNull(ContentType.IMAGE_JPEG);
        assertNotNull(ContentType.IMAGE_GIF);
        assertNotNull(ContentType.IMAGE_SVG);
    }

    @Test
    void enumValueOf() {
        // Test that valueOf works correctly
        assertEquals(ContentType.APPLICATION_JSON, ContentType.valueOf("APPLICATION_JSON"));
        assertEquals(ContentType.TEXT_PLAIN, ContentType.valueOf("TEXT_PLAIN"));
        assertEquals(ContentType.IMAGE_PNG, ContentType.valueOf("IMAGE_PNG"));
    }

    @Test
    void enumValues() {
        // Test that values() returns all enum constants
        ContentType[] values = ContentType.values();
        assertEquals(15, values.length);
    }
}
