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
                .contains("Agentic RAG 证据研究")
                .contains("renderEvidenceResearch(w.evidenceResearch)")
                .contains("rag.agentic-evidence-research")
                .contains("方案证据绑定")
                .contains("renderSolutionGrounding(w.solutionGrounding")
                .contains("agent.solution-grounding")
                .contains("Claim–Evidence 语义审查")
                .contains("renderSolutionCritique(w.solutionCritique")
                .contains("agent.claim-evidence-critic")
                .contains("未执行模型语义判定，请人工复核")
                .doesNotContain(
                        "<section id=\"knowledgePanel\" "
                                + "class=\"panel knowledge hidden\">"
                );
    }
}
