package com.dev.codingagent.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

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

    private String extractFromPdf(InputStream inputStream) throws IOException {
        log.info("📑  Extracting text from PDF...");
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("✅  PDF pages processed: {}", document.getNumberOfPages());
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

    // Add this method to DocumentParserService — accepts raw bytes instead of MultipartFile
    public String extractTextFromBytes(byte[] bytes, String fileName) throws IOException {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf"  -> extractFromPdf(new java.io.ByteArrayInputStream(bytes));
            case "docx" -> extractFromDocx(new java.io.ByteArrayInputStream(bytes));
            case "txt"  -> new String(bytes);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}