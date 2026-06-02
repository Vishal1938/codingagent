package com.dev.codingagent.service;

import com.dev.codingagent.dto.QuestionAnswer;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.properties.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    @Value("${solver.output.dir}")
    private String outputDir;

//    private final SupabaseStorageService storageService;

    @Autowired
    private CloudinaryStorageService storageService;


//    public PdfGeneratorService(
//            SupabaseStorageService storageService
//    ) {
//        this.storageService = storageService;
//    }

    public Map<String,String> generatePdf(String jobId, String sourceFileName,
                              List<QuestionAnswer> answers,String userEmail) throws IOException {

        Map<String,String> pdfData= new HashMap<>();

        // Ensure output directory exists
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        String pdfPath = outputDir + "/result_" + jobId + ".pdf";
        log.info("📄  Generating PDF at: {}", pdfPath);

        try (PdfWriter writer = new PdfWriter(pdfPath);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            // ── Title ──────────────────────────────────────────
            doc.add(new Paragraph("Question & Answer Report")
                    .setFontSize(22)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Source: " + sourceFileName)
                    .setFontSize(11)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Generated: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("Total Questions: " + answers.size())
                    .setFontSize(11)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new LineSeparator(new SolidLine()));
            doc.add(new Paragraph("\n"));

            // ── Q&A content ────────────────────────────────────
            for (QuestionAnswer qa : answers) {
                doc.add(new Paragraph("Q" + qa.id() + ". " + qa.question())
                        .setFontSize(13)
                        .setBold());

                doc.add(new Paragraph("Type: " + qa.type())
                        .setFontSize(10)
                        .setItalic());

                doc.add(new Paragraph("Answer: " + qa.answer())
                        .setFontSize(12));

                doc.add(new LineSeparator(new SolidLine(0.5f)));
                doc.add(new Paragraph("\n"));
            }
        }

        File pdfFile = new File(pdfPath);
        pdfData.put("pdfFile",pdfPath);
        String publicUrl = storageService.uploadPdf(
                pdfFile,
                 jobId ,userEmail
        );
        pdfData.put("publicUrl",publicUrl);

        // optional cleanup
        //        pdfFile.delete();
        log.info("✅  PDF generated successfully: {}", pdfPath);
        return pdfData;
    }
}