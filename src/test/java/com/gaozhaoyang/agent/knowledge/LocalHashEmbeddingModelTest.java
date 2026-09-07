package com.gaozhaoyang.agent.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LocalHashEmbeddingModelTest {

    private final LocalHashEmbeddingModel embeddingModel =
            new LocalHashEmbeddingModel();

    @Test
    void shouldCreateNormalizedVectorWithFixedDimensions() {
        float[] vector = embeddingModel.embed("订单导出");

        double squaredLength = 0.0;
        for (float value : vector) {
            squaredLength += value * value;
        }

        assertThat(vector).hasSize(512);
        assertThat(Math.sqrt(squaredLength)).isCloseTo(
                1.0,
                within(0.0001)
        );
    }

    @Test
    void shouldGiveRelatedTextHigherCosineSimilarity() {
        float[] query = embeddingModel.embed("订单全量导出");
        float[] related = embeddingModel.embed("导出全部订单数据");
        float[] unrelated = embeddingModel.embed("修改首页背景颜色");

        assertThat(cosine(query, related))
                .isGreaterThan(cosine(query, unrelated));
    }

    private double cosine(float[] left, float[] right) {
        double product = 0.0;
        for (int index = 0; index < left.length; index++) {
            product += left[index] * right[index];
        }
        return product;
    }
}
