package com.gaozhaoyang.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardKnowledgeUiTest {

    @Test
    void shouldShowCorpusStatusAndLoadOnlyBoundedChunkPreview() throws Exception {
        String dashboard = new ClassPathResource("static/dashboard.html")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(dashboard)
                .contains("<section id=\"knowledgePanel\" class=\"panel knowledge\">")
                .contains("api('/api/knowledge/status')")
                .contains("api('/api/knowledge/index/status')")
                .contains("/api/knowledge/chunks?limit=60")
                .contains("scrollIntoView({behavior:'smooth',block:'start'})")
                .contains("检索范围仍是全部 ${status.chunks} 个 Chunk")
                .doesNotContain(
                        "<section id=\"knowledgePanel\" "
                                + "class=\"panel knowledge hidden\">"
                );
    }
}
