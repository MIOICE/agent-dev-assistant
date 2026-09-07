package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 仅用于本地学习与降级演示的稀疏哈希向量模型。
 * 它能够衡量字符及二元词组重合度，但不具备神经网络语义理解能力。
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();

        for (int index = 0; index < inputs.size(); index++) {
            embeddings.add(new Embedding(
                    vectorize(inputs.get(index)),
                    index
            ));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        StringBuilder embeddingContent = new StringBuilder(
                document.getText()
        );

        Object title = document.getMetadata().get("title");
        Object keywords = document.getMetadata().get("keywords");
        if (title != null) {
            embeddingContent.append(' ').append(title);
        }
        if (keywords != null) {
            embeddingContent.append(' ').append(keywords);
        }

        return vectorize(embeddingContent.toString());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        int[] codePoints = normalize(text).codePoints().toArray();

        for (int index = 0; index < codePoints.length; index++) {
            addFeature(vector, "U:" + codePoints[index], 0.35f);

            if (index + 1 < codePoints.length) {
                String bigram = "B:"
                        + codePoints[index]
                        + ':'
                        + codePoints[index + 1];
                addFeature(vector, bigram, 1.0f);
            }
        }

        normalizeLength(vector);
        return vector;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    private void addFeature(float[] vector, String feature, float weight) {
        int hash = feature.hashCode();
        int vectorIndex = Math.floorMod(hash, DIMENSIONS);
        float sign = (Integer.rotateLeft(hash, 16) & 1) == 0
                ? 1.0f
                : -1.0f;
        vector[vectorIndex] += weight * sign;
    }

    private void normalizeLength(float[] vector) {
        double squaredLength = 0.0;
        for (float value : vector) {
            squaredLength += value * value;
        }

        if (squaredLength == 0.0) {
            return;
        }

        double length = Math.sqrt(squaredLength);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= (float) length;
        }
    }
}
