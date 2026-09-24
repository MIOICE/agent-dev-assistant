package com.gaozhaoyang.agent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesKnowledgeDocumentLoaderTest {

    @TempDir
    Path root;

    private final MesKnowledgeDocumentLoader loader =
            new MesKnowledgeDocumentLoader();

    @Test
    void shouldLoadAllowedBusinessPageAndExtractPathMetadata()
            throws IOException {
        write(
                "summary/订单执行/订单管理/生产订单列表.md",
                """
                        # 订单执行 · 订单管理 · 生产订单列表

                        ## 页面职责
                        负责生产订单的建立、查询和导入。
                        """
        );

        MesKnowledgeDocumentLoader.LoadResult result =
                loader.load(root, 20, 32_768);

        assertThat(result.documents()).hasSize(1);
        BusinessDocument document = result.documents().getFirst();
        assertThat(document.id()).isEqualTo(
                "MES:summary/订单执行/订单管理/生产订单列表.md"
        );
        assertThat(document.title()).isEqualTo(
                "订单执行 · 订单管理 · 生产订单列表"
        );
        assertThat(document.businessModule()).isEqualTo("订单执行");
        assertThat(document.businessCategory()).isEqualTo("订单管理");
        assertThat(document.documentType()).isEqualTo("BUSINESS_PAGE");
        assertThat(document.sourceType()).isEqualTo("external-mes");
        assertThat(document.keywords()).contains("订单执行", "订单管理");
    }

    @Test
    void shouldAllowOnlyCuratedRequirementDocuments() throws IOException {
        write(
                "document/REQ-003-需求分析.md",
                "# 需求分析\n\n订单建立字段需要与流程卡对齐。"
        );
        write(
                "document/REQ-003-自测报告.md",
                "# 自测报告\n\n登录并进行测试。"
        );
        write(
                "document/正式服SQL更新清单.md",
                "# SQL 更新\n\nALTER TABLE example;"
        );
        write(
                "summary/README.md",
                "# 知识库索引\n\n不应作为业务页面载入。"
        );
        write(
                "summary/系统设置/系统设置/API管理.md",
                "# API 管理\n\n目录节点，无独立 Controller/Action。"
        );

        MesKnowledgeDocumentLoader.LoadResult result =
                loader.load(root, 20, 32_768);

        assertThat(result.documents())
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.documentType())
                            .isEqualTo("REQUIREMENT_ANALYSIS");
                    assertThat(document.sourcePath())
                            .isEqualTo("document/REQ-003-需求分析.md");
                });
        assertThat(result.skippedDocuments()).isEqualTo(4);
    }

    @Test
    void shouldSanitizeInfrastructureAndCredentialValues()
            throws IOException {
        write(
                "summary/系统设置/用户管理/系统用户管理.md",
                """
                        # 系统用户管理

                        password: very-secret
                        服务：http://127.0.0.1:8002/admin
                        邮箱：owner@example.com
                        手机：13812345678
                        本地目录：C:\\private\\customer\\config.ini
                        """
        );

        MesKnowledgeDocumentLoader.LoadResult result =
                loader.load(root, 20, 32_768);

        BusinessDocument document = result.documents().getFirst();
        assertThat(document.sanitized()).isTrue();
        assertThat(result.sanitizedDocuments()).isEqualTo(1);
        assertThat(document.content())
                .contains("[REDACTED]", "[IP已脱敏]", "[邮箱已脱敏]",
                        "[手机号已脱敏]", "[本地路径已脱敏]")
                .doesNotContain("very-secret", "127.0.0.1",
                        "owner@example.com", "13812345678", "C:\\private");
    }

    @Test
    void shouldRejectMissingRootWithSafeMessage() {
        Path missing = root.resolve("not-created");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                loader.load(missing, 20, 32_768)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MES 外部知识库目录不存在或不可读");
    }

    private void write(String relativePath, String content)
            throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
