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

    @Value("${mail.provider:smtp}")   // "smtp" or "resend"
    private String mailProvider;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point — picks provider based on mail.provider property
    // ─────────────────────────────────────────────────────────────────────────

    public void sendResultEmail(String toEmail, String jobId,
                                String fileName, String pdfPath) throws Exception {
        log.info("📧  Sending result email to: {} via provider: {}", toEmail, mailProvider);

        if ("resend".equalsIgnoreCase(mailProvider)) {
            sendViaResend(toEmail, jobId, fileName, pdfPath);
        } else {
            sendViaSmtp(toEmail, jobId, fileName, pdfPath);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Option 1 — Gmail SMTP (original implementation)
    // Works locally. Blocked on Railway port 587 — switch to Resend.
    // ─────────────────────────────────────────────────────────────────────────

    public void sendViaSmtp(String toEmail, String jobId,
                            String fileName, String pdfPath) throws MessagingException {
        log.info("📧  [SMTP] Sending to: {}", toEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Your Q&A Results are Ready — " + fileName);
        helper.setText(buildEmailBody(fileName, jobId));

        FileSystemResource file = new FileSystemResource(new File(pdfPath));
        helper.addAttachment("QA_Report_" + jobId + ".pdf", file);

        mailSender.send(message);
        log.info("✅  [SMTP] Email sent successfully to: {}", toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Option 2 — Resend API (works on Railway — sends over HTTPS not SMTP)
    // Docs: https://resend.com/docs/api-reference/emails/send-email
    // ─────────────────────────────────────────────────────────────────────────

    public void sendViaResend(String toEmail, String jobId,
                              String fileName, String pdfPath) throws IOException, InterruptedException {

        log.info("📧  [Resend] Sending to: {}", toEmail);

        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("Resend API key is not configured (resend.api-key)");
        }

        // Read PDF and encode as Base64 for the attachment
        byte[] pdfBytes   = Files.readAllBytes(new File(pdfPath).toPath());
        String pdfBase64  = Base64.getEncoder().encodeToString(pdfBytes);
        String attachName = "QA_Report_" + jobId + ".pdf";

        // Build JSON payload manually — no extra dependency needed
        // Resend API: POST https://api.resend.com/emails
        String jsonBody = """
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "Your Q&A Results are Ready — %s",
                  "html": "%s",
                  "attachments": [
                    {
                      "filename": "%s",
                      "content": "%s"
                    }
                  ]
                }
                """.formatted(
                resendFromEmail,
                toEmail,
                escapeJson(fileName),
                escapeJson(buildEmailBodyHtml(fileName, jobId)),
                attachName,
                pdfBase64
        );

        HttpClient  client  = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("✅  [Resend] Email sent successfully to: {} | response: {}", toEmail, response.body());
        } else {
            log.error("❌  [Resend] Failed to send email | status: {} | body: {}",
                    response.statusCode(), response.body());
            throw new IOException("Resend API error: " + response.statusCode() + " — " + response.body());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildEmailBody(String fileName, String jobId) {
        return """
                Hello,
                
                Your question paper has been processed successfully!
                
                File: %s
                Job ID: %s
                
                Please find the complete Q&A report attached as a PDF.
                
                Regards,
                Question Solver
                """.formatted(fileName, jobId);
    }

    private String buildEmailBodyHtml(String fileName, String jobId) {
        return """
                <div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px">
                  <h2 style="color:#111">Your Q&amp;A Report is Ready ✅</h2>
                  <p>Your question paper has been processed successfully.</p>
                  <table style="margin:16px 0;border-collapse:collapse;width:100%%">
                    <tr>
                      <td style="padding:8px 12px;background:#f3f4f6;font-weight:600;width:80px">File</td>
                      <td style="padding:8px 12px;border-bottom:1px solid #e5e7eb">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 12px;background:#f3f4f6;font-weight:600">Job ID</td>
                      <td style="padding:8px 12px;border-bottom:1px solid #e5e7eb;font-family:monospace;font-size:12px">%s</td>
                    </tr>
                  </table>
                  <p>The complete Q&amp;A report is attached as a PDF.</p>
                  <p style="color:#6b7280;font-size:12px;margin-top:32px">Question Solver — AI-powered document analysis</p>
                </div>
                """.formatted(fileName, jobId);
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