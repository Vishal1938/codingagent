package com.dev.codingagent.doubt.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits document text into overlapping chunks for embedding.
 *
 * Fixed-size with overlap: ~800 char chunks, ~120 char overlap so context
 * isn't lost at boundaries. Tries to break on sentence/paragraph boundaries
 * when possible rather than mid-word.
 */
@Service
public class TextChunkerService {

    private static final int CHUNK_SIZE   = 800;
    private static final int OVERLAP      = 120;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        text = text.replaceAll("\\s+", " ").trim();
        int len = text.length();
        int start = 0;

        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);

            // Try to break on a sentence boundary near the end
            if (end < len) {
                int boundary = findBoundary(text, start + CHUNK_SIZE - OVERLAP, end);
                if (boundary > start) end = boundary;
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);

            if (end >= len) break;
            start = end - OVERLAP;   // overlap with previous chunk
            if (start < 0) start = 0;
        }

        return chunks;
    }

    /** Find a sentence-ending boundary (. ! ?) within [from, to], else return to. */
    private int findBoundary(String text, int from, int to) {
        for (int i = to - 1; i > from; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return to;
    }
}