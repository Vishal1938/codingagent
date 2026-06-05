package com.dev.codingagent.solver.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(CloudinaryStorageService.class);

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadPdf(File file, String jobId, String userEmail) throws IOException {

        String safeEmail = userEmail
                .replace("@", "_")
                .replace(".", "_");

        String publicId =
                "reports/"
                        + safeEmail
                        + "/"
                        + jobId
                        + "/"
                        + file.getName();

        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                file,
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "public_id",     publicId,
                        "overwrite",     true,
                        "use_filename",  false,
                        "access_mode",   "public",   // ← explicit public access
                        "type",          "upload"    // ← upload type (not authenticated)
                )
        );

        String url = uploadResult.get("secure_url").toString();
        log.info("✅ PDF uploaded to Cloudinary: {}", url);
        return url;
    }
}