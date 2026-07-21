package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.domain.exception.SourceResolutionException;
import com.lanny.ailab.rag.domain.model.SourceType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts normalized text from already-loaded source payloads.
 */
@Component
class SourceTextExtractor {

    private static final int MAX_RESOLVED_CONTENT_LENGTH = 1_000_000;

    String extractDownloadedText(SourceType type, String uri, String contentType, byte[] body) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
        String lowerUri = uri == null ? "" : uri.toLowerCase();

        if (normalizedContentType.contains("pdf") || lowerUri.endsWith(".pdf")) {
            return extractPdf(body);
        }
        if (normalizedContentType.contains("wordprocessingml.document") || lowerUri.endsWith(".docx")) {
            return extractDocx(body);
        }
        if (normalizedContentType.contains("text/html") || lowerUri.endsWith(".html") || lowerUri.endsWith(".htm")) {
            return htmlToText(new String(body, StandardCharsets.UTF_8));
        }
        if (type == SourceType.GOOGLE_DRIVE && normalizedContentType.contains("application/json")) {
            throw new SourceResolutionException("Google Drive export returned JSON instead of downloadable content");
        }
        return normalizePlainText(new String(body, StandardCharsets.UTF_8), type + " source resolved to blank text");
    }

    String extractPdf(byte[] body) {
        try (PDDocument document = Loader.loadPDF(body)) {
            return normalizePlainText(new PDFTextStripper().getText(document), "PDF source resolved to blank text");
        } catch (IOException ex) {
            throw new SourceResolutionException("Failed to extract text from PDF source", ex);
        }
    }

    String extractDocx(byte[] body) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(body));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalizePlainText(extractor.getText(), "DOCX source resolved to blank text");
        } catch (IOException ex) {
            throw new SourceResolutionException("Failed to extract text from DOCX source", ex);
        }
    }

    String htmlToText(String html) {
        return normalizePlainText(Jsoup.parse(html).text(), "HTML source resolved to blank text");
    }

    String normalizePlainText(String content, String blankMessage) {
        if (!hasText(content)) {
            throw new IllegalArgumentException(blankMessage);
        }

        String normalized = content.replace('\u0000', ' ').trim();
        if (!hasText(normalized)) {
            throw new IllegalArgumentException(blankMessage);
        }
        return normalized;
    }

    String limitContent(String content) {
        if (content.length() > MAX_RESOLVED_CONTENT_LENGTH) {
            throw new SourceResolutionException(
                    "Resolved source content exceeds the maximum supported size of "
                            + MAX_RESOLVED_CONTENT_LENGTH + " characters");
        }
        return content;
    }

    void appendSeparated(StringBuilder builder, String value) {
        if (!hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(value.trim());
    }

    boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
