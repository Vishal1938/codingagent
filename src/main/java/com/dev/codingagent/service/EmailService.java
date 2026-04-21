package com.dev.codingagent.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.io.File;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResultEmail(String toEmail, String jobId,
                                String fileName, String pdfPath) throws MessagingException {
        log.info("📧  Sending result email to: {}", toEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Your Q&A Results are Ready — " + fileName);
        helper.setText("""
                Hello,
                
                Your question paper has been processed successfully!
                
                File: %s
                Job ID: %s
                
                Please find the complete Q&A report attached as a PDF.
                
                Regards,
                Question Solver
                """.formatted(fileName, jobId));

        FileSystemResource file = new FileSystemResource(new File(pdfPath));
        helper.addAttachment("QA_Report_" + jobId + ".pdf", file);

        mailSender.send(message);
        log.info("✅  Email sent successfully to: {}", toEmail);
    }
}