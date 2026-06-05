package com.dev.codingagent.solver.service;


import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Centralized OCR service — used by both DocumentParserService and
 * PageQuestionExtractorService when PDFBox's text-layer extraction
 * returns empty/insufficient text (image-based PDFs / scanned hard copies).
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    // Render at 300 DPI — good accuracy/speed trade-off
    private static final int RENDER_DPI = 300;

    // OS-specific path to tessdata (the language model files)
    // Windows: typically  C:/Program Files/Tesseract-OCR/tessdata
    // Linux (Railway/Docker after apt install): /usr/share/tesseract-ocr/4.00/tessdata
    // Configured via env var so the same code runs both locally and on Railway
    @Value("${ocr.tessdata-path}")
    private String tessdataPath;

    @Value("${ocr.languages:eng}")
    private String languages;   // "eng" or "eng+hin" for English+Hindi etc.

    /**
     * OCR a single PDF page.
     *
     * @param document   open PDDocument
     * @param pageNumber 1-based page index
     * @return extracted text — never null, "" on failure
     */
    public String ocrPage(PDDocument document, int pageNumber) {
        try {
            log.info("🔍  Running OCR on page {} at {} DPI...", pageNumber, RENDER_DPI);

            PDFRenderer renderer = new PDFRenderer(document);
            // PDFBox uses 0-based page index here
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, RENDER_DPI);

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(languages);
            tesseract.setPageSegMode(1);   // automatic page segmentation with OSD
            tesseract.setOcrEngineMode(1); // LSTM neural net — best accuracy

            String text = tesseract.doOCR(image).trim();
            log.info("🔍  OCR page {} — extracted {} chars", pageNumber, text.length());
            return text;

        } catch (Exception e) {
            log.error("❌  OCR failed for page {}: {}", pageNumber, e.getMessage());
            return "";
        }
    }

    /**
     * OCR every page of a PDF — used by DocumentParserService when the
     * entire document has no text layer.
     */
    public String ocrEntireDocument(PDDocument document) {
        StringBuilder sb = new StringBuilder();
        int numPages = document.getNumberOfPages();
        log.info("🔍  Running OCR on entire document ({} pages)...", numPages);

        for (int i = 1; i <= numPages; i++) {
            String pageText = ocrPage(document, i);
            if (!pageText.isBlank()) {
                sb.append(pageText).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}