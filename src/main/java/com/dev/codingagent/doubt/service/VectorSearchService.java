package com.dev.codingagent.doubt.service;

import com.dev.codingagent.doubt.entity.DocumentChunk;
import com.dev.codingagent.doubt.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * In-app vector similarity search (Option C).
 *
 * Loads a document's chunks, computes cosine similarity against the query
 * embedding in memory, returns the top-K most relevant. Fine for per-user
 * personal-document scale. Swap for Atlas Vector Search / Pinecone later
 * without changing the retrieval interface.
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final DocumentChunkRepository chunkRepository;

    public VectorSearchService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /** Return the top-K chunks of a document most similar to the query embedding. */
    public List<DocumentChunk> topK(String documentId, List<Double> queryEmbedding, int k) {
        List<DocumentChunk> chunks = chunkRepository.findByDocumentId(documentId);
        if (chunks.isEmpty()) return List.of();

        return chunks.stream()
                .sorted(Comparator.comparingDouble(
                                (DocumentChunk c) -> cosineSimilarity(queryEmbedding, c.getEmbedding()))
                        .reversed())
                .limit(k)
                .toList();
    }

    /** Cosine similarity between two vectors. */
    public static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return -1.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i), y = b.get(i);
            dot   += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) return -1.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}