# 面向企业存量系统的需求开发 Agent

一个可运行、可演示的 Java Agent 项目。系统把自然语言需求转换为结构化需求卡片，通过本地 RAG 检索企业规范，生成技术方案，并用人工审批完成安全闭环。

## 已实现能力

- 需求结构化：标题、背景、模块、验收标准、缺失信息、优先级、风险。
- 多轮澄清：信息不足时暂停，补充后使用同一 `workflowId` 恢复。
- 业务友好澄清：将问题分为阻塞项与非阻塞假设，每轮最多展示 4 个关键问题，支持选项式回答和一键采用 Agent 推荐值。
- Agentic RAG：由 DeepSeek 将明确需求拆成 1 至 5 项可验证的证据需求，Harness 在最多 2 轮、6 次查询预算内执行本地 BGE 混合检索；未命中时改写查询，并持久化规划、检索轨迹、证据与缺口。Mock 模式使用可解释规则规划，模型规划失败时也会安全降级。
- 方案证据绑定：把摘要、后端、数据库、API、安全、性能、测试和回滚结论逐项关联到证据计划实际命中的 Chunk；引用不存在或专项证据缺失时标记为未支撑，待确认内容单独标记为假设，并计算不含假设的证据关联率。
- 企业知识检索：可选只读加载外部 MES Markdown，按标题层级分块并附带模块、分类、文档类型和来源路径；本地 BGE 语义召回与关键词召回合并重排，支持来源去重、置信度拒答、引用和离线评测。
- 增量向量索引：使用Chunk SHA-256指纹识别新增、修改和删除内容，将`SimpleVectorStore`与索引清单原子保存到磁盘；语料和模型版本未变化时直接热加载，仅变化时计算受影响向量。
- Tool Calling：业务文档检索工具和只读数据库元数据目录工具。
- 标准 MCP Server：通过 Streamable HTTP 暴露 2 个可发现的只读工具，提供 JSON Schema、只读语义提示、白名单、参数校验和隐私化审计。
- 方案生成：后端改动、数据库影响、API、安全、性能、测试、回滚和待确认项。
- Human-in-the-loop：方案审批、驳回意见回传、重新生成、最终完成。
- 可靠性：失败状态持久化、日志记录、原工作流重试、参数校验、统一异常响应。
- 持久化：内存 / MySQL 可切换，工作流列表、阶段筛选、版本号和事件时间线。
- 可视化操作台：历史工作流、页面恢复、澄清、审批、驳回、重试、知识库评测。
- Trace 与运行评测：持久化需求分析、RAG、方案生成等节点的状态、耗时和统计属性，展示首轮就绪率、平均澄清轮数、推荐值采纳率及完成率。
- Durable Coding Agent：API 提交后立即进入有界后台队列；从已审批技术方案生成受限 Java 补丁，在无网络 Docker 沙箱中离线测试，失败时依据构建证据最多自动修复两轮，并通过文件 Checkpoint 支持服务重启恢复。
- Agent Skills：以标准 `SKILL.md` 封装 Java 生成、失败修复、安全复核和导出可靠性方法；先按生成/修复阶段缩小候选集，再用本地 BGE 语义相似度按需加载正文，并记录路由分数与任务级激活轨迹。
- Skill 供应链防护：启动时校验技能名称、阶段、宿主工具白名单、内容大小和 SHA-256 受信任清单；摘要不一致或越权声明会直接拒绝启动。
- 安全发布：每轮修复保持文件集合不变，持续执行路径、危险能力和自治预算校验；最终通过统一 Diff、SHA-256 与二次人工审批输出独立产物。
- 工程验证：JUnit 5、MockMvc、H2 MySQL 兼容测试和 Docker Compose。

详细设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 目录重点

```text
src/main/java/com/gaozhaoyang/agent
├─ requirement/   需求分析、结构化输出与确定性校验
├─ knowledge/     文档分块、向量检索与评测
├─ solution/      技术方案生成
├─ tool/          Spring AI 工具调用
├─ workflow/      状态机、审批闭环与持久化
├─ coding/        异步代码任务、Checkpoint、有界修复与沙箱验证
├─ skill/         Skills 索引、阶段路由、渐进式加载与激活统计
└─ common/        统一异常与运行状态
```

## 本地启动（推荐先用 Mock）

Mock 模式不消耗大模型额度，但仍会执行完整工作流和本地 BGE 检索。

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Set-Location "F:\agent-dev-assistant"
mvn test
mvn spring-boot:run
```

打开：`http://localhost:8080/`。根地址会进入新版工作台。

## 使用真实 DeepSeek

只在当前 PowerShell 会话设置密钥，不要写入配置文件：

```powershell
$env:DEEPSEEK_API_KEY = "替换成你自己的密钥"
$env:APP_AI_MODE = "deepseek"

Set-Location "F:\agent-dev-assistant"
mvn spring-boot:run
```

页面右上角会显示“真实模型”或“Mock 演示”，可以直接确认当前运行方式。

### 不使用 PowerShell：VS Code 一键启动

1. 用 VS Code 打开整个 `F:\agent-dev-assistant` 文件夹。
2. 如果项目根目录还没有 `.env.local`，复制 `.env.example` 并命名为 `.env.local`，然后打开它。
3. 将 `DEEPSEEK_API_KEY=` 后面的占位内容替换成自己的密钥并保存。
4. 打开左侧“运行和调试”，在顶部选择 `Agent - DeepSeek`。
5. 点击绿色运行按钮或按 `F5`。

`.vscode/launch.json` 已配置好主类、工作目录和环境变量文件；`.env.local` 已写入 `.gitignore`，不会被正常提交到 Git。需要切回演示模式时选择 `Agent - Mock` 即可。

### 接入本地 MES 业务文档

外部知识库默认关闭，原始业务文件不会被复制进项目。要启用时，在 `.env.local` 中增加：

```dotenv
MES_KNOWLEDGE_ENABLED=true
MES_KNOWLEDGE_ROOT=F:/path/to/sanitized-mes-workspace
MES_KNOWLEDGE_MAX_FILES=400
MES_KNOWLEDGE_MAX_FILE_BYTES=131072
KNOWLEDGE_INDEX_ENABLED=true
KNOWLEDGE_INDEX_ROOT=F:/agent-workspaces/knowledge-index
KNOWLEDGE_INDEX_MODEL_ID=bge-small-zh-v1.5-v1
```

然后在 VS Code 选择 `Agent - Mock` 或 `Agent - DeepSeek` 并按 `F5`。启动后工作台会常驻展示源文档、Chunk、MES 私有文档和向量索引状态；点击“查看知识库”只渲染前 60 个 Chunk，混合检索仍覆盖全部语料，避免把分页预览数量误认为知识库总量。加载器只允许读取 `summary` 下的业务页面及 `document` 下的需求卡片、需求分析和整体方案；其他文件会被跳过，原目录始终只读。

`KNOWLEDGE_INDEX_MODEL_ID`代表Embedding模型与预处理规则的版本。更换模型、Tokenizer或向量处理逻辑时必须修改该值，系统会自动判定旧快照不兼容并全量重建。向量快照仍包含经过基础脱敏的文档Chunk，应当与原始业务资料采用相同的私有数据保护策略，不能提交Git。

启动完成后可直接在浏览器打开 `http://localhost:8080/` 操作。也可以用下面四条完整命令验证新接口：

```text
curl.exe "http://localhost:8080/api/knowledge/status"
curl.exe "http://localhost:8080/api/knowledge/index/status"
curl.exe --get "http://localhost:8080/api/knowledge/search" --data-urlencode "query=生产订单列表有哪些字段、接口和核心数据表"
curl.exe "http://localhost:8080/api/knowledge/evaluation"
```

## MySQL 持久化启动

先按 `scripts/create-workflow-database.sql` 创建数据库和应用账号，再执行：

```powershell
$env:WORKFLOW_REPOSITORY = "mysql"
$env:WORKFLOW_MYSQL_USERNAME = "agent_app"
$env:WORKFLOW_MYSQL_PASSWORD = "替换成你的本地数据库密码"
$env:APP_AI_MODE = "mock"

Set-Location "F:\agent-dev-assistant"
mvn spring-boot:run
```

密码只保留在当前终端环境变量中，不要提交到 Git。

## Docker Compose

复制 `.env.example` 为 `.env`，填写数据库密码和本地 BGE 模型目录，然后运行：

```powershell
Set-Location "F:\agent-dev-assistant"
Copy-Item .env.example .env
docker compose up --build
```

## 完整接口测试命令

### 创建工作流

```powershell
$body = @{
    requirement = "紧急：订单列表增加全量导出，仅管理员可操作，导出当前筛选结果CSV，超过一万条使用异步任务"
} | ConvertTo-Json

$workflow = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/workflows" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $body

$workflow | ConvertTo-Json -Depth 12
$workflowId = $workflow.workflowId
```

### 查询、补充、审批、驳回与重试

```powershell
# 查询详情
Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/$workflowId" -Method Get |
    ConvertTo-Json -Depth 12

# 信息不足时补充
$clarificationBody = @{
    clarification = "导出当前筛选订单，CSV格式，仅管理员可用，超过一万条采用异步任务"
} | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/workflows/$workflowId/clarifications" `
    -Method Post -ContentType "application/json; charset=utf-8" `
    -Body $clarificationBody | ConvertTo-Json -Depth 12

# 驳回并重新生成
$rejectionBody = @{ feedback = "补充异步任务进度查询和失败重试设计" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/workflows/$workflowId/rejection" `
    -Method Post -ContentType "application/json; charset=utf-8" `
    -Body $rejectionBody | ConvertTo-Json -Depth 12

# 审批通过
$approvalBody = @{ comment = "安全、性能和回滚方案已确认" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/workflows/$workflowId/approval" `
    -Method Post -ContentType "application/json; charset=utf-8" `
    -Body $approvalBody | ConvertTo-Json -Depth 12

# 只有 FAILED 状态可以重试
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/workflows/$workflowId/retry" `
    -Method Post -ContentType "application/json; charset=utf-8" -Body "{}" |
    ConvertTo-Json -Depth 12
```

### 工作流列表和系统状态

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/workflows?page=0&size=20" -Method Get |
    ConvertTo-Json -Depth 8

Invoke-RestMethod -Uri "http://localhost:8080/api/workflows?stage=WAITING_APPROVAL&page=0&size=20" -Method Get |
    ConvertTo-Json -Depth 8

Invoke-RestMethod -Uri "http://localhost:8080/api/system/status" -Method Get |
    Format-List
```

## 关键接口

| 方法 | 地址 | 作用 |
|---|---|---|
| POST | `/api/workflows` | 创建并推进工作流 |
| GET | `/api/workflows` | 分页查询工作流，可按阶段筛选 |
| GET | `/api/workflows/{id}` | 查询完整快照和事件历史 |
| GET | `/api/workflows/{id}/trace` | 查询单次工作流 Agent Span |
| GET | `/api/workflows/metrics` | 查询工作流质量与运行指标 |
| POST | `/api/workflows/{id}/clarifications` | 补充需求并继续 |
| POST | `/api/workflows/{id}/clarifications/recommendations` | 采用当前阻塞问题的推荐值并继续 |
| POST | `/api/workflows/{id}/approval` | 审批通过 |
| POST | `/api/workflows/{id}/rejection` | 驳回并携带意见重生成 |
| POST | `/api/workflows/{id}/retry` | 重试失败工作流 |
| GET | `/api/knowledge/search?query=...` | 混合检索并返回向量分、综合分与来源元数据 |
| GET | `/api/knowledge/status` | 查看内置/外部文档、分块、跳过和脱敏统计 |
| GET | `/api/knowledge/index/status` | 查看冷启动、热加载、增量更新及Chunk复用统计 |
| GET | `/api/knowledge/evaluation` | 运行检索评测 |
| POST | `/api/mcp` | MCP Streamable HTTP 协议入口 |
| GET | `/api/tools/status` | 查看 MCP 暴露边界和工具策略 |
| GET | `/api/tools/audits` | 查看最近工具调用审计 |
| GET | `/api/skills` | 查看可发现的 Agent Skills 元数据与激活次数 |
| POST | `/api/coding-tasks?workflowId=...` | 提交后台代码任务，返回 HTTP 202 与当前快照 |
| GET | `/api/coding-tasks/{taskId}` | 轮询后台阶段、Checkpoint、修复与构建记录 |
| GET | `/api/coding-tasks/workflow/{workflowId}` | 查询工作流对应的代码任务 |
| POST | `/api/coding-tasks/{taskId}/approval` | 人工批准测试通过的代码产物 |
| GET | `/api/system/status` | 查看模型与仓库运行模式 |

## 简历表述边界

可以如实写“Spring AI、DeepSeek、有界 Agentic RAG、证据规划与多轮查询改写、证据充分性检查、方案结论与 Chunk 级来源绑定、未支撑结论检测、本地 BGE RAG、外部 MES 文档只读接入、标题感知分块、向量与关键词混合检索、Chunk指纹、磁盘向量快照、增量索引、来源元数据与置信度拒答、Tool Calling、MCP Streamable HTTP Server、Agent Skills 语义路由与渐进式加载、Skill SHA-256 完整性校验、工具白名单与调用审计、结构化澄清、确定性策略校验、Agent Trace、运行评测、工作流状态机、Human-in-the-loop、MySQL 工作流持久化、文件 Checkpoint、异步后台任务、有界自动修复、受限代码补丁、统一 Diff、自治预算与 Docker 隔离验证”。当前没有真正实现 Redis、标准 OpenTelemetry Exporter、分布式任务队列、MCP 身份认证、第三方 Skill 签名、Qdrant/PGVector等独立向量数据库、多实例索引锁、直接修改真实仓库、生产发布和真实业务库查询，不应写成已经完成。

本轮“业务友好澄清 Agent”的实现与面试复述见 [docs/MILESTONE-01-BUSINESS-CLARIFICATION.md](docs/MILESTONE-01-BUSINESS-CLARIFICATION.md)。

Agent Trace 与运行评测见 [docs/MILESTONE-02-TRACE-AND-EVALS.md](docs/MILESTONE-02-TRACE-AND-EVALS.md)。

标准 MCP Server 与工具治理见 [docs/MILESTONE-03-MCP-TOOL-GOVERNANCE.md](docs/MILESTONE-03-MCP-TOOL-GOVERNANCE.md)。

安全代码工作区与自治预算见 [docs/MILESTONE-04-SAFE-CODING-SANDBOX.md](docs/MILESTONE-04-SAFE-CODING-SANDBOX.md)。

后台执行、Checkpoint 与有界自动修复见 [docs/MILESTONE-05-DURABLE-REPAIR-LOOP.md](docs/MILESTONE-05-DURABLE-REPAIR-LOOP.md)。

Agent Skills 与渐进式上下文加载见 [docs/MILESTONE-06-AGENT-SKILLS.md](docs/MILESTONE-06-AGENT-SKILLS.md)。

Skills 语义路由与供应链校验见 [docs/MILESTONE-07-SEMANTIC-SKILL-ROUTING.md](docs/MILESTONE-07-SEMANTIC-SKILL-ROUTING.md)。

私有 MES 语料治理与混合 RAG 见 [docs/MILESTONE-08-PRIVATE-MES-RAG.md](docs/MILESTONE-08-PRIVATE-MES-RAG.md)。

持久化快照与增量向量索引见 [docs/MILESTONE-09-INCREMENTAL-VECTOR-INDEX.md](docs/MILESTONE-09-INCREMENTAL-VECTOR-INDEX.md)。

有界 Agentic RAG 证据研究见 [docs/MILESTONE-10-AGENTIC-RAG.md](docs/MILESTONE-10-AGENTIC-RAG.md)。

方案证据绑定与未支撑检测见 [docs/MILESTONE-11-SOLUTION-GROUNDING.md](docs/MILESTONE-11-SOLUTION-GROUNDING.md)。

累计面试复述与追问答案见 [docs/INTERVIEW-GUIDE.md](docs/INTERVIEW-GUIDE.md)。
