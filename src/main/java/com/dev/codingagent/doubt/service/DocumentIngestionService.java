package com.dev.codingagent.doubt.service;

import com.dev.codingagent.doubt.dto.DocJob;
import com.dev.codingagent.doubt.dto.DocJobStore;
import com.dev.codingagent.doubt.entity.DocumentChunk;
import com.dev.codingagent.doubt.entity.DocumentEntity;
import com.dev.codingagent.doubt.repository.DocumentChunkRepository;
import com.dev.codingagent.doubt.repository.DocumentRepository;
import com.dev.codingagent.solver.service.DocumentParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Async ingestion pipeline for a doubt-chat document:
 *   parse text → chunk → embed each chunk → store chunks + document.
 *
 * Reuses the existing DocumentParserService (with OCR fallback) so scanned
 * notes work too.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentParserService documentParser;
    private final TextChunkerService        chunker;
    private final EmbeddingService          embeddingService;
    private final DocumentRepository        documentRepository;
    private final DocumentChunkRepository   chunkRepository;
    private final DocJobStore               jobStore;

    public DocumentIngestionService(DocumentParserService documentParser,
                                    TextChunkerService chunker,
                                    EmbeddingService embeddingService,
                                    DocumentRepository documentRepository,
                                    DocumentChunkRepository chunkRepository,
                                    DocJobStore jobStore) {
        this.documentParser     = documentParser;
        this.chunker            = chunker;
        this.embeddingService   = embeddingService;
        this.documentRepository = documentRepository;
        this.chunkRepository    = chunkRepository;
        this.jobStore           = jobStore;
    }

    @Async
    public void ingestAsync(String jobId, byte[] fileBytes, String fileName,
                            String title, String userEmail) {
        DocJob job = jobStore.get(jobId);
        try {
            job.setStatus("PROCESSING");

            // 1. Parse text (OCR fallback inside)
            log.info("📖  [Doc {}] parsing {}", jobId, fileName);
            String text = documentParser.extractTextFromBytes(fileBytes, fileName);
            if (text == null || text.isBlank()) {
                fail(job, "No readable text found in the document.");
                return;
            }

            // 2. Create the Document shell
            String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "");
            DocumentEntity doc = new DocumentEntity();
            doc.setDocumentId(documentId);
            doc.setUserId(userEmail);
            doc.setTitle(title != null && !title.isBlank() ? title : stripExt(fileName));
            doc.setSourceFileName(fileName);
            doc.setStatus("PROCESSING");
            documentRepository.save(doc);
            job.setDocumentId(documentId);

            // 3. Chunk
            List<String> chunkTexts = chunker.chunk(text);
            log.info("📖  [Doc {}] {} chunks", jobId, chunkTexts.size());
            if (chunkTexts.isEmpty()) {
                documentRepository.deleteByDocumentId(documentId);
                fail(job, "Document could not be split into chunks.");
                return;
            }

            // 4. Embed all chunks (batched)
            List<List<Double>> embeddings = embeddingService.embedBatch(chunkTexts);

            // 5. Persist chunks
            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setChunkId("chk_" + UUID.randomUUID().toString().replace("-", ""));
                chunk.setDocumentId(documentId);
                chunk.setUserId(userEmail);
                chunk.setText(chunkTexts.get(i));
                chunk.setEmbedding(i < embeddings.size() ? embeddings.get(i) : null);
                chunk.setChunkIndex(i);
                chunks.add(chunk);
            }
            chunkRepository.saveAll(chunks);

            // 6. Finalize document
            doc.setChunkCount(chunks.size());
            doc.setStatus("READY");
            documentRepository.save(doc);

            job.setChunkCount(chunks.size());
            job.setStatus("DONE");
            log.info("✅  [Doc {}] ready — {} chunks embedded", jobId, chunks.size());

        } catch (Exception e) {
            log.error("❌  [Doc {}] ingestion failed: {}", jobId, e.getMessage(), e);
            fail(job, "Processing failed: " + e.getMessage());
        }
    }

    private void fail(DocJob job, String msg) {
        job.setStatus("FAILED");
        job.setError(msg);
    }

    private String stripExt(String name) {
        if (name == null) return "Document";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}