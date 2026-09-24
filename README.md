# 客户需求与实施方案协作 Agent

面向 MES 实施团队的内部 Agent 系统。实施人员把会议纪要、工单或群聊中确认的客户需求录入系统，系统负责结构化需求、提示关键业务缺口、调查对应客户与 MES 版本的授权资料，并生成可交研发评审的技术方案初稿。客户不登录系统；实施人员核对、修订和批准后，系统只生成客户沟通稿。

> 本项目不会连接客户生产库执行写操作，不会自动部署，也不会把模型生成内容称为已经验证的实施方案。

## 当前架构

```text
实施人员工作台
      │ OIDC/JWT，tenant_id 来自登录令牌
      ▼
Java Case Orchestrator（Spring Boot / Spring AI）
      │ 需求状态机、澄清、预算、审批、版本化方案
      │ A2A 1.0 JSON-RPC + 限时服务 JWT + traceparent
      ▼
Python MES Knowledge Agent（FastAPI / 官方 A2A SDK）
      │ 客户 + 系统 + 版本前置过滤
      │ 语义召回 + 关键词召回 + RRF 融合
      ▼
ai-platform 知识空间 / 只读工具
```

两个协议的职责不同：

- A2A 用于 Java 编排 Agent 向独立运行的 Python 知识 Agent 委派证据调查任务，并通过远端 `taskId` 恢复任务。
- MCP 用于知识 Agent 或其他授权 Agent 访问只读资料与工具，不用来伪装多 Agent。

## 已实现能力

- 实施人员内部工作台：录入客户原始需求、记录线下澄清结果、查看证据、修改方案、驳回和批准。
- Durable Case：Case、系统版本快照、澄清记录、A2A 任务 ID、证据包、方案修订版和审批审计持久化；乐观锁避免重复推进。
- 真正的 A2A 协作：Java 使用官方 A2A Java SDK，Python 使用官方 A2A Python SDK；支持 Agent Card、任务状态、结构化 Artifact 和远端任务续接。
- 知识边界：Python 端先按 `tenant_id + system_id + system_version` 找到唯一知识空间，再执行检索；未登记版本返回证据不足。
- 混合检索：语义候选与关键词候选通过 RRF 融合，限制单文档 Chunk 数量；Java 端对多问题结果去重、检查覆盖度并保留未解决问题。
- 证据治理：来源、文档、Chunk、版本、语义分和关键词分随方案保存；版本冲突、跨租户结果、提示注入内容会被隔离。
- Human-in-the-loop：只有实施人员可查看技术方案、逐条修订和批准；证据不足时禁止生成客户沟通稿。
- 身份隔离：Case 的客户范围来自 JWT，而不是前端请求；同一 Case 使用其他 `tenant_id` 查询时返回不存在。
- 试点配置：`pilot` Profile 强制 MySQL、OIDC 和真实 A2A，不允许悄悄回退到 H2 或开发令牌。
- 旧能力隔离：历史 Coding Agent 位于独立 Maven 模块，不被主应用启动；旧 `/api/workflows` 默认只读并仅管理员可查看。

## Maven 模块

```text
agent-dev-assistant/
├─ case-orchestrator/       当前主应用：需求 Case、A2A 编排、证据与方案审批
├─ coding-agent-archive/    历史 Coding Agent 演示模块，不属于主流程
├─ docs/                    架构、检索优化、运行与里程碑文档
└─ local-career/            本地简历和面试材料，已被 .gitignore 排除
```

Python 知识 Agent 位于独立项目：`F:/APPS/phpstudy_pro/WWW/ai-platform/services/knowledge_a2a/`。

## 本地启动

先在 ai-platform 的 IDE 终端启动知识 Agent：

```text
.venv\Scripts\python.exe -m uvicorn services.knowledge_a2a.app:app --host 127.0.0.1 --port 8020
```

再在本项目根目录的 IDE 终端启动 Java：

```text
mvn -pl case-orchestrator spring-boot:run
```

两端至少配置相同的 `A2A_SIGNING_SECRET`，Java 端另需：

```dotenv
KNOWLEDGE_A2A_ENABLED=true
KNOWLEDGE_A2A_ENDPOINT=http://127.0.0.1:8020/a2a/jsonrpc
```

浏览器打开 `http://localhost:8080/`。本地演示默认使用 H2、开发 JWT 和 Mock 模型。

试点环境使用：

```text
mvn -pl case-orchestrator spring-boot:run -Dspring-boot.run.profiles=pilot
```

`pilot` Profile 必须提供 MySQL、OIDC 和真实 A2A 配置；完整变量见 [.env.example](.env.example)。

## 验证

Java 全量测试：

```text
mvn test
```

Python 检索与 A2A 测试：

```text
.venv\Scripts\python.exe -m pytest -o addopts="" tests/test_hybrid_retrieval.py tests/test_knowledge_a2a.py -q
```

## API 主流程

| 方法 | 地址 | 作用 |
|---|---|---|
| POST | `/api/cases` | 实施人员创建客户需求 Case；支持 `Idempotency-Key` |
| GET | `/api/cases` | 只查询当前令牌客户范围内的 Case |
| GET | `/api/cases/{id}` | 查询阶段、澄清问题、证据缺口和进度 |
| POST | `/api/cases/{id}/clarifications` | 记录实施人员线下确认的客户反馈 |
| GET | `/api/cases/{id}/technical-proposal` | 查看技术方案、证据包与修订历史 |
| PUT | `/api/cases/{id}/technical-proposal` | 保存实施人员修订版 |
| POST | `/api/cases/{id}/reject` | 驳回当前方案 |
| POST | `/api/cases/{id}/approve` | 批准方案并允许生成客户沟通稿 |
| POST | `/api/cases/{id}/cancel` | 取消 Case，并尽力取消远端 A2A Task |
| GET | `/api/cases/{id}/business-proposal` | 读取已批准的客户沟通稿 |

## 文档

- [当前系统架构](docs/ARCHITECTURE.md)
- [检索优化设计](docs/RETRIEVAL-OPTIMIZATION.md)
- [开发与运行手册](docs/RUNBOOK.md)
- [A2A 实施里程碑](docs/MILESTONE-23-A2A-CASE-ORCHESTRATOR.md)

既有里程碑文档仍保留，用于说明被归档能力的演进历史；若与当前主流程冲突，以本 README 和 `MILESTONE-23` 为准。
