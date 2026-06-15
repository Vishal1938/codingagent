package com.dev.codingagent.doubt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps Spring AI's EmbeddingModel (backed by OpenAI text-embedding-3-small).
 *
 * Spring AI auto-configures an EmbeddingModel bean when spring-ai-openai is on
 * the classpath and the API key is set — same key as your chat model.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Embed a single text into a vector. */
    public List<Double> embed(String text) {
        float[] vector = embeddingModel.embed(text);
        return toDoubleList(vector);
    }

    /** Embed many texts. Returns vectors index-aligned with input. */
    public List<List<Double>> embedBatch(List<String> texts) {
        List<List<Double>> result = new ArrayList<>();
        // Spring AI's embed(List) batches under the hood
        List<float[]> vectors = embeddingModel.embed(texts);
        for (float[] v : vectors) {
            result.add(toDoubleList(v));
        }
        return result;
    }

    private List<Double> toDoubleList(float[] vector) {
        List<Double> list = new ArrayList<>(vector.length);
        for (float f : vector) list.add((double) f);
        return list;
    }
}