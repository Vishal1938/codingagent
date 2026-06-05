//package com.dev.codingagent.service;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.FileSystemResource;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestClient;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//
//@Service
//public class SupabaseStorageService {
//
//    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);
//    private static final int MAX_RETRIES = 2;
//
//    @Value("${supabase.url}")
//    private String supabaseUrl;
//
//    @Value("${supabase.api.key}")
//    private String apiKey;
//
//    @Value("${supabase.bucket}")
//    private String bucket;
//
//    // RestClient — modern replacement for RestTemplate in Spring Boot 3.x
//    private final RestClient restClient = RestClient.create();
//
//    /**
//     * Uploads a PDF to Supabase Storage.
//     *
//     * Object is namespaced as: userEmail/jobId/fileName.pdf
//     * This prevents collisions between users and makes cleanup easy per user.
//     *
//     * @return public URL of the uploaded file
//     */
//    public String uploadPdf(File file, String jobId,
//                            String userEmail) throws IOException {
//
//        // Namespace: userEmail/jobId/originalFileName
//        // Replace @ and . in email so it's a valid path segment
//        String safeEmail  = userEmail.replace("@", "_").replace(".", "_");
//        String objectName = file.getName();
//
//        return uploadWithRetry(file, objectName, 1);
//    }
//
//    // ── Retry wrapper ─────────────────────────────────────────────────────
//
//    private String uploadWithRetry(File file, String objectName,
//                                   int attempt) throws IOException {
//        try {
//            return doUpload(file, objectName);
//        } catch (Exception e) {
//            if (attempt <= MAX_RETRIES) {
//                log.warn("⚠️  Supabase upload attempt {}/{} failed — retrying: {}",
//                        attempt, MAX_RETRIES, e.getMessage());
//                return uploadWithRetry(file, objectName, attempt + 1);
//            }
//            log.error("❌  Supabase upload permanently failed after {} attempts", MAX_RETRIES);
//            throw new RuntimeException("Failed to upload PDF to Supabase", e);
//        }
//    }
//
//    // ── Core upload ───────────────────────────────────────────────────────
//
//    private String doUpload(File file, String objectName) throws IOException {
//
//        String uploadUrl = supabaseUrl
//                + "/storage/v1/object/"
//                + bucket + "/"
//                + objectName;
//
//        byte[] fileBytes = Files.readAllBytes(file.toPath());
//
//        // RestClient — cleaner, non-deprecated, Spring Boot 3.x native
//        restClient.post()
//                .uri(uploadUrl)
//                .header("Authorization", "Bearer " + apiKey)
//                .header("x-upsert", "true")   // upsert: overwrite if same path exists
//                .contentType(MediaType.APPLICATION_OCTET_STREAM)
//                .body(fileBytes)
//                .retrieve()
//                .toBodilessEntity();           // throws if non-2xx
//
//        String publicUrl = supabaseUrl
//                + "/storage/v1/object/public/"
//                + bucket + "/"
//                + objectName;
//
//        log.info("✅  PDF uploaded to Supabase: {}", publicUrl);
//        return publicUrl;
//    }
//
//    /**
//     * Generates a signed URL that expires after `expiresInSeconds`.
//     * Use this instead of the public URL if your bucket is private.
//     *
//     * Call this in the download endpoint to give the user a short-lived link.
//     */
//    public String getSignedUrl(String objectName, long expiresInSeconds) {
//        String signedUrlEndpoint = supabaseUrl
//                + "/storage/v1/object/sign/"
//                + bucket + "/"
//                + objectName;
//
//        String body = "{\"expiresIn\": " + expiresInSeconds + "}";
//
//        String response = restClient.post()
//                .uri(signedUrlEndpoint)
//                .header("Authorization", "Bearer " + apiKey)
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(body)
//                .retrieve()
//                .body(String.class);
//
//        // Response: {"signedURL": "/storage/v1/object/sign/bucket/path?token=..."}
//        // Extract the signed URL and prefix with supabase base URL
//        String signedPath = response
//                .replace("{\"signedURL\":\"", "")
//                .replace("\"}", "");
//
//        return supabaseUrl + signedPath;
//    }
//}