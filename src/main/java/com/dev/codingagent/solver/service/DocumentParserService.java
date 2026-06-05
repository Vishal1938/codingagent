package com.dev.codingagent.solver.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    // Threshold below which a PDF page is considered image-based → fall back to OCR
    private static final int OCR_FALLBACK_CHAR_THRESHOLD = 50;

    private final OcrService ocrService;

    public DocumentParserService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public String extractText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        log.info("📄  Parsing document: {}", fileName);
        log.info("📦  File size: {} bytes", file.getSize());
        log.info("📋  Content type: {}", file.getContentType());

        if (fileName == null) {
            throw new IllegalArgumentException("File name is missing");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        log.info("🔍  Detected file type: {}", extension);

        String extractedText = switch (extension) {
            case "pdf"  -> extractFromPdf(file.getInputStream());
            case "docx" -> extractFromDocx(file.getInputStream());
            case "txt"  -> extractFromTxt(file.getInputStream());
            default -> throw new IllegalArgumentException(
                    "Unsupported file type: " + extension + ". Supported: pdf, docx, txt"
            );
        };

        log.info("✅  Text extracted successfully — {} characters", extractedText.length());
        return extractedText;
    }

    /**
     * PDF extraction with OCR fallback for image-based PDFs.
     *
     * Strategy:
     * 1. Try PDFBox's text-layer extraction first (fast, ~5ms per page)
     * 2. If total extracted text is too small relative to page count,
     *    treat it as an image-based PDF and run OCR on every page (~3-5s per page)
     */
    private String extractFromPdf(InputStream inputStream) throws IOException {
        log.info("📑  Extracting text from PDF...");
        try (PDDocument document = PDDocument.load(inputStream)) {

            // Try standard text extraction first
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            int numPages = document.getNumberOfPages();
            log.info("📑  PDFBox extracted {} chars from {} pages", text.length(), numPages);

            // Heuristic: if avg text per page < threshold, this is likely a scanned PDF
            int avgCharsPerPage = numPages > 0 ? text.length() / numPages : 0;
            if (avgCharsPerPage < OCR_FALLBACK_CHAR_THRESHOLD) {
                log.warn("⚠️  PDF appears image-based (avg {} chars/page) — running OCR fallback",
                        avgCharsPerPage);
                String ocrText = ocrService.ocrEntireDocument(document);
                log.info("✅  OCR fallback complete — {} chars extracted", ocrText.length());
                return ocrText;
            }

            log.info("✅  PDF text layer is sufficient — no OCR needed");
            return text;
        }
    }

    private String extractFromDocx(InputStream inputStream) throws IOException {
        log.info("📝  Extracting text from DOCX...");
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs()
                    .stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining("\n"));
            log.info("✅  DOCX paragraphs processed: {}", document.getParagraphs().size());
            return text;
        }
    }

    private String extractFromTxt(InputStream inputStream) throws IOException {
        log.info("📃  Extracting text from TXT...");
        return new String(inputStream.readAllBytes());
    }

    /** Accepts raw bytes — used by AsyncQuestionSolverService. */
    public String extractTextFromBytes(byte[] bytes, String fileName) throws IOException {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf"  -> extractFromPdf(new ByteArrayInputStream(bytes));
            case "docx" -> extractFromDocx(new ByteArrayInputStream(bytes));
            case "txt"  -> new String(bytes);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}