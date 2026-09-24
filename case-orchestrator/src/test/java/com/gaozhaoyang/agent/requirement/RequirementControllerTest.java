package com.gaozhaoyang.agent.requirement;

import com.gaozhaoyang.agent.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequirementControllerTest {

    private MockMvc mockMvc;
    private String receivedContent;

    @BeforeEach
    void setUp() {
        RequirementAnalyzer fakeAnalyzer = content -> {
            receivedContent = content;
            return new RequirementCard(
                    content,
                    content,
                    List.of("订单管理"),
                    List.of("请求被正确处理"),
                    List.of(),
                    "P2",
                    List.of(),
                    List.of("DOC-ORDER-001｜订单管理规范"),
                    true
            );
        };

        mockMvc = createMockMvc(fakeAnalyzer);
    }

    @Test
    void shouldRejectBlankContent() throws Exception {
        mockMvc.perform(post("/api/requirements/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("需求描述不能为空"));
    }

    @Test
    void shouldRejectContentLongerThanFourThousandCharacters() throws Exception {
        String requestJson = """
                {"content":"%s"}
                """.formatted("需".repeat(4001));

        mockMvc.perform(post("/api/requirements/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("需求描述不能超过4000字"));
    }

    @Test
    void shouldPassValidContentToAnalyzerAndReturnCard() throws Exception {
        String content = "订单列表增加客户筛选";

        mockMvc.perform(post("/api/requirements/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"订单列表增加客户筛选"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(content))
                .andExpect(jsonPath("$.priority").value("P2"))
                .andExpect(jsonPath("$.references[0]")
                        .value("DOC-ORDER-001｜订单管理规范"))
                .andExpect(jsonPath("$.readyForPlanning").value(true));

        assertThat(receivedContent).isEqualTo(content);
    }

    @Test
    void shouldReturnServiceUnavailableWhenAnalyzerFails() throws Exception {
        RequirementAnalyzer failingAnalyzer = content -> {
            throw new RequirementAnalysisException(
                    "需求分析服务暂时不可用",
                    new RuntimeException("模拟模型连接失败")
            );
        };
        MockMvc failingMockMvc = createMockMvc(failingAnalyzer);

        failingMockMvc.perform(post("/api/requirements/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"订单列表增加导出功能"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("需求分析服务暂时不可用"));
    }

    private MockMvc createMockMvc(RequirementAnalyzer analyzer) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return standaloneSetup(new RequirementController(analyzer))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
