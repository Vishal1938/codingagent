package com.dev.codingagent.service;

import com.dev.codingagent.dto.QuestionAnswer;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    @Value("${solver.output.dir}")
    private String outputDir;

    @Autowired
    private CloudinaryStorageService storageService;

    // Matches both inline math \( ... \) and display math \[ ... \]
    // Group 1: inline math content; Group 2: display math content
    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\\\\\((.+?)\\\\\\)|\\\\\\[(.+?)\\\\\\]",
            Pattern.DOTALL);

    public Map<String, String> generatePdf(String jobId, String sourceFileName,
                                           List<QuestionAnswer> answers,
                                           String userEmail) throws IOException {

        Map<String, String> pdfData = new HashMap<>();

        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        String pdfPath = outputDir + "/result_" + jobId + ".pdf";
        log.info("📄  Generating PDF at: {}", pdfPath);

        try (PdfWriter writer = new PdfWriter(pdfPath);
             PdfDocument pdf  = new PdfDocument(writer);
             Document doc     = new Document(pdf)) {

            // ── Title ──────────────────────────────────────────────────────
            doc.add(new Paragraph("Question & Answer Report")
                    .setFontSize(22).setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Source: " + sourceFileName)
                    .setFontSize(11)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Generated: " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Total Questions: " + answers.size())
                    .setFontSize(11)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new LineSeparator(new SolidLine()));
            doc.add(new Paragraph("\n"));

            // ── Q&A content ────────────────────────────────────────────────
            for (QuestionAnswer qa : answers) {

                // Question — also pass through math renderer in case question has LaTeX
                doc.add(new Paragraph("Q" + qa.id() + ". ")
                        .setFontSize(13).setBold());
                renderTextWithMath(doc, qa.question(), 13, true);

                // Type
                doc.add(new Paragraph("Type: " + qa.type())
                        .setFontSize(10).setItalic());

                // Answer — the critical part with math
                doc.add(new Paragraph("Answer:").setFontSize(12).setBold());
                renderTextWithMath(doc, qa.answer(), 12, false);

                doc.add(new LineSeparator(new SolidLine(0.5f)));
                doc.add(new Paragraph("\n"));
            }
        }

        File pdfFile = new File(pdfPath);
        pdfData.put("pdfFile", pdfPath);

        String publicUrl = storageService.uploadPdf(pdfFile, jobId, userEmail);
        pdfData.put("publicUrl", publicUrl);

        log.info("✅  PDF generated successfully: {}", pdfPath);
        return pdfData;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Math-aware text rendering
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Splits text by LaTeX delimiters \(...\) and \[...\], rendering plain
     * text as paragraphs and LaTeX as embedded images.
     */
    private void renderTextWithMath(Document doc, String text, float fontSize, boolean bold) {
        if (text == null || text.isBlank()) return;

        Matcher matcher = LATEX_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {

            // Plain text before the math expression
            if (matcher.start() > lastEnd) {
                String plain = text.substring(lastEnd, matcher.start()).trim();
                if (!plain.isEmpty()) {
                    addPlainParagraph(doc, plain, fontSize, bold);
                }
            }

            // Extract the LaTeX — group 1 = inline, group 2 = display
            String inlineLatex  = matcher.group(1);
            String displayLatex = matcher.group(2);
            String latex        = inlineLatex != null ? inlineLatex : displayLatex;
            boolean isDisplay   = displayLatex != null;

            try {
                Image mathImg = renderLatexToImage(latex, isDisplay);
                if (isDisplay) {
                    // Display math — centered on its own line
                    mathImg.setTextAlignment(TextAlignment.CENTER);
                    doc.add(new Paragraph().add(mathImg)
                            .setTextAlignment(TextAlignment.CENTER));
                } else {
                    // Inline math — embed in a paragraph
                    doc.add(new Paragraph().add(mathImg));
                }
            } catch (Exception e) {
                log.warn("⚠️  Failed to render LaTeX '{}' — falling back to text: {}",
                        latex, e.getMessage());
                addPlainParagraph(doc, latex, fontSize, bold);
            }

            lastEnd = matcher.end();
        }

        // Remaining plain text after the last match
        if (lastEnd < text.length()) {
            String tail = text.substring(lastEnd).trim();
            if (!tail.isEmpty()) {
                addPlainParagraph(doc, tail, fontSize, bold);
            }
        }
    }

    /** Plain prose paragraph — used for non-math text segments. */
    private void addPlainParagraph(Document doc, String text, float fontSize, boolean bold) {
        Paragraph p = new Paragraph(text).setFontSize(fontSize);
        if (bold) p.setBold();
        doc.add(p);
    }

    /**
     * Renders a LaTeX string to a PNG image embeddable in the PDF.
     * Uses JLatexMath — pure Java, no external dependencies.
     *
     * @param latex     the raw LaTeX (without delimiters)
     * @param isDisplay true for display math (larger), false for inline
     */
    private Image renderLatexToImage(String latex, boolean isDisplay) throws IOException {

        // Render at a LARGE size first, then scale down — gives sharper text
        // Display math gets bigger font (visual emphasis), inline matches body text
        int fontSize = isDisplay ? 40 : 32;
        int style    = isDisplay ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT;

        TeXFormula formula = new TeXFormula(latex);
        TeXIcon icon       = formula.createTeXIcon(style, fontSize);
        icon.setInsets(new Insets(6, 6, 6, 6));

        BufferedImage image = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());

        JLabel label = new JLabel();
        label.setForeground(Color.BLACK);
        icon.paintIcon(label, g2, 0, 0);
        g2.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        Image pdfImg = new Image(ImageDataFactory.create(baos.toByteArray()));

        // Scale down: render at high res, display at readable size
        // Display: ~75% of rendered → looks prominent
        // Inline:  ~50% of rendered → matches 12pt body text
        float scale = isDisplay ? 0.30f : 0.32f;
        pdfImg.scale(scale, scale);

        return pdfImg;
    }
}