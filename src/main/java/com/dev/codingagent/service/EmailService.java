package com.dev.codingagent.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:noreply@resend.dev}")
    private String resendFromEmail;

    @Value("${mail.provider:smtp}")
    private String mailProvider;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point — picks provider based on mail.provider property
    // Now accepts publicUrl so both attachment AND download link are included
    // ─────────────────────────────────────────────────────────────────────────

    public void sendResultEmail(String toEmail, String jobId,
                                String fileName, String pdfPath,
                                String publicUrl) throws Exception {
        log.info("📧  Sending result email to: {} via provider: {}", toEmail, mailProvider);

        if ("resend".equalsIgnoreCase(mailProvider)) {
            sendViaResend(toEmail, jobId, fileName, pdfPath, publicUrl);
        } else {
            sendViaSmtp(toEmail, jobId, fileName, pdfPath, publicUrl);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Option 1 — Gmail SMTP
    // Attaches PDF + includes download link in body
    // ─────────────────────────────────────────────────────────────────────────

    public void sendViaSmtp(String toEmail, String jobId, String fileName,
                            String pdfPath, String publicUrl) throws MessagingException {
        log.info("📧  [SMTP] Sending to: {}", toEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Your Q&A Results are Ready — " + fileName);
        helper.setText(buildEmailBodyPlain(fileName, jobId, publicUrl), false);

        // Attach PDF if file exists on disk
        File pdfFile = new File(pdfPath);
        if (pdfFile.exists()) {
            helper.addAttachment("QA_Report_" + jobId + ".pdf",
                    new FileSystemResource(pdfFile));
            log.info("📎  PDF attached from disk: {}", pdfPath);
        } else {
            log.warn("⚠️  PDF not found on disk — sending link only: {}", pdfPath);
        }

        mailSender.send(message);
        log.info("✅  [SMTP] Email sent successfully to: {}", toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Option 2 — Resend API
    // Attaches PDF as Base64 + includes download link in HTML body
    // ─────────────────────────────────────────────────────────────────────────

    public void sendViaResend(String toEmail, String jobId, String fileName,
                              String pdfPath, String publicUrl)
            throws IOException, InterruptedException {

        log.info("📧  [Resend] Sending to: {}", toEmail);

        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Resend API key not configured (resend.api-key)");
        }

        // Build attachments block — include PDF if file still exists on disk
        String attachmentsJson = "";
        File pdfFile = new File(pdfPath);
        if (pdfFile.exists()) {
            byte[] pdfBytes  = Files.readAllBytes(pdfFile.toPath());
            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
            attachmentsJson  = """
                    "attachments": [
                        {
                          "filename": "QA_Report_%s.pdf",
                          "content": "%s"
                        }
                      ],
                    """.formatted(jobId, pdfBase64);
            log.info("📎  PDF attached from disk ({} bytes)", pdfBytes.length);
        } else {
            log.warn("⚠️  PDF not found on disk — sending link only: {}", pdfPath);
        }

        String jsonBody = """
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "Your Q&A Results are Ready — %s",
                  "html": "%s",
                  %s
                  "tags": [{"name": "jobId", "value": "%s"}]
                }
                """.formatted(
                resendFromEmail,
                toEmail,
                escapeJson(fileName),
                escapeJson(buildEmailBodyHtml(fileName, jobId, publicUrl)),
                attachmentsJson,
                jobId
        );

        HttpClient  client  = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("✅  [Resend] Email sent successfully to: {} | response: {}",
                    toEmail, response.body());
        } else {
            log.error("❌  [Resend] Failed | status: {} | body: {}",
                    response.statusCode(), response.body());
            throw new IOException("Resend API error: "
                    + response.statusCode() + " — " + response.body());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Email body builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildEmailBodyPlain(String fileName, String jobId, String publicUrl) {
        String linkSection = (publicUrl != null && !publicUrl.isBlank())
                ? "\nDownload link (also available online): " + publicUrl + "\n"
                : "";
        return """
                Hello,

                Your question paper has been processed successfully!

                File:   %s
                Job ID: %s
                %s
                The complete Q&A report is attached as a PDF.
                If the attachment is unavailable, use the download link above.

                Regards,
                Question Solver
                """.formatted(fileName, jobId, linkSection);
    }

    private String buildEmailBodyHtml(String fileName, String jobId, String publicUrl) {
        // Download button — only shown when publicUrl is available
        String downloadBtn = (publicUrl != null && !publicUrl.isBlank())
                ? """
                  <a href="%s"
                     style="display:inline-block;margin:20px 0;padding:12px 24px;
                            background:#111;color:#fff;border-radius:8px;
                            text-decoration:none;font-weight:600;font-size:14px">
                    ↓ Download PDF Report
                  </a>
                  """.formatted(publicUrl)
                : "";

        return """
                <div style="font-family:system-ui,sans-serif;max-width:560px;
                            margin:0 auto;padding:32px 24px;color:#111">

                  <div style="margin-bottom:24px">
                    <div style="display:inline-block;background:#111;color:#fff;
                                border-radius:8px;padding:6px 12px;
                                font-size:12px;font-weight:600;letter-spacing:0.5px">
                      QUESTION SOLVER
                    </div>
                  </div>

                  <h2 style="font-size:22px;font-weight:700;margin:0 0 8px">
                    Your Q&amp;A Report is Ready ✅
                  </h2>
                  <p style="color:#6b7280;margin:0 0 24px;font-size:14px;line-height:1.6">
                    Your question paper has been processed successfully.
                    The PDF report is attached to this email.
                  </p>

                  <table style="width:100%%;border-collapse:collapse;
                                border:1px solid #e5e7eb;border-radius:8px;
                                overflow:hidden;font-size:13px;margin-bottom:8px">
                    <tr>
                      <td style="padding:10px 14px;background:#f9fafb;
                                 font-weight:600;width:90px;color:#374151">File</td>
                      <td style="padding:10px 14px;border-left:1px solid #e5e7eb">%s</td>
                    </tr>
                    <tr style="border-top:1px solid #e5e7eb">
                      <td style="padding:10px 14px;background:#f9fafb;font-weight:600;color:#374151">Job ID</td>
                      <td style="padding:10px 14px;border-left:1px solid #e5e7eb;
                                 font-family:monospace;font-size:11px;color:#6b7280">%s</td>
                    </tr>
                  </table>

                  %s

                  <p style="color:#9ca3af;font-size:11px;margin-top:32px;
                             border-top:1px solid #f3f4f6;padding-top:16px">
                    If the attachment didn't arrive, use the download button above.
                    This link is permanently available as long as your account is active.
                  </p>
                </div>
                """.formatted(fileName, jobId, downloadBtn);
    }

    /** Escape special characters for embedding in a JSON string value. */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}