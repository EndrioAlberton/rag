/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Converts HTML content to Markdown using the flexmark-html2md library.
 */
@ApplicationScoped
public class HtmlToMarkdownService {

    private static final FlexmarkHtmlConverter CONVERTER = FlexmarkHtmlConverter.builder().build();

    /**
     * Converts the given HTML string to Markdown.
     *
     * @param html the HTML content to convert
     * @return the Markdown representation, or empty string if conversion fails
     */
    public String toMarkdown(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            return CONVERTER.convert(html);
        } catch (Exception e) {
            Log.warn("⚠️ Falha ao converter HTML para Markdown", e);
            return "";
        }
    }
}
