/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */

package dev.rpmhub.infrastructure.service;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PDFExtractorService {

    @ConfigProperty(name = "rag.location", defaultValue = "src/main/resources/rag")
    String ragLocation;

    /**
     * Extracts text from a PDF file located at the given path.
     *
     * @param path the path to the PDF file
     * @return the extracted text as a String
     */
    public String extractText(Path path) {
        String text = "";
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            // Verifica se o documento não está criptografado
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            } else {
                Log.error("The document is encrypted.");
            }
        } catch (IOException e) {
            Log.error("Could not read the file.");
        }
        return text;
    }

    /**
     * Checks if the given file path points to a PDF file.
     *
     * @param filePath the path to the file
     * @return true if the file is a PDF, false otherwise
     */
    public boolean isPdfFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

}
