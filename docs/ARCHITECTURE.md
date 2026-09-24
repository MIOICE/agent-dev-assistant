# 当前系统架构

## 1. 产品边界

系统服务于公司内部 MES 实施人员，不提供客户自助入口。实施人员仍通过会议、工单或企业沟通工具与客户确认需求，再把确认结果录入本系统。

系统输出分为两层：

1. 技术方案初稿：包含业务依据、影响模块、接口/数据影响、风险、测试和回滚，供实施与研发评审。
2. 客户沟通稿：只在实施人员完成证据核对和批准后生成，不暴露内部实现细节。

自动修改客户系统、生产数据写入、部署、巡检和自动处置不在当前范围。

## 2. 组件职责

### Java Case Orchestrator

- 接收实施人员录入的客户原始需求。
- 使用模型做需求语义结构化，由 Java 规则决定状态迁移和阻塞问题。
- 保存不可变的客户系统版本快照。
- 通过 A2A 委派知识调查，保存远端任务 ID 并支持重启续接。
- 校验返回证据的租户、系统和版本，隔离提示注入内容。
- 基于受控证据生成方案初稿，保存每次人工修订和审批审计。

### Python MES Knowledge Agent

- 作为独立 A2A Agent 发布 Agent Card 和 `mes-evidence-research` Skill。
- 独立验证 Java 服务凭证中的签发者、受众、过期时间、scope 和 tenant。
- 把一次需求拆出的多个调查问题限制在指定知识空间内检索。
- 返回结构化 `EvidenceBundle`，而不是不可审计的一段自然语言答案。
- 使用持久化 A2A TaskStore，使 Java 可凭远端任务 ID 查询或恢复。

### ai-platform 知识层

- 保存知识空间、文档、Chunk、向量和客户/系统/版本映射。
- 只允许已经登记的精确版本进入 A2A 检索。
- 提供向量召回、关键词召回与只读工具能力。

## 3. 主流程

```text
创建 Case
  → ANALYZING_REQUIREMENT
  → 缺少关键业务信息：WAITING_FOR_CLARIFICATION
  → 实施人员线下确认后录入答案
  → RESEARCHING_EVIDENCE
  → 创建或续接 A2A Task
  → Python 进行版本限定的混合检索
  → 证据不足：EVIDENCE_INSUFFICIENT（不生成事实结论）
  → 证据充分：GENERATING_SOLUTION
  → WAITING_FOR_IMPLEMENTER_REVIEW
  → 实施人员修改：产生新的不可变修订版
  → 驳回：REVISION_REQUESTED
  → 批准：PUBLISHED（允许生成客户沟通稿）
  → 取消：CANCELED，并尽力调用 A2A CancelTask
```

Case 后台调度采用“至少一次提交 + 数据库乐观锁”。重复调度可能发生，但同一版本只有一个工作线程能成功推进。服务重启后定时扫描可恢复阶段；如果已经存在 `remoteTaskId`，Java 查询原 A2A Task，而不是重复发起调查。

## 4. 数据模型

`RequirementCase` 保存 tenant、创建人、原始需求、客户系统版本快照、状态、结构化需求、澄清记录、远端任务 ID、证据包、当前方案、修订历史、审批、错误信息和乐观锁版本。

试点 Profile 使用 Flyway 管理 MySQL 表。H2 只用于本地演示与自动化测试。

## 5. 安全边界

- 客户范围取自登录 JWT 的 `tenant_id`，创建请求不能指定租户。
- 主流程只允许 `IMPLEMENTER` 和 `ADMIN`；客户没有系统账号和页面。
- 跨服务 JWT 有两分钟有效期，固定 issuer、audience 和 `knowledge:read` scope。
- Python 再次校验 tenant，并只选择精确匹配客户、系统与版本的空间。
- 文档内容是数据，不是指令；Java 证据策略会隔离常见提示注入模式。
- 证据不足、版本未登记、远端失败和版本冲突都会显式停住，不能静默生成。
- 历史 `/api/workflows` 默认只读且仅管理员可查看；历史 Coding Agent 不随主应用启动。

## 6. A2A 与 MCP

A2A 描述 Agent 到 Agent 的任务委派：调用方看到任务状态、Artifact、取消语义和 Agent Card。MCP 描述 Agent 到工具/资源的受控访问。当前架构中 Java 到 Python 知识 Agent 使用 A2A；Python 知识 Agent访问资料或只读工具使用 RAG/MCP。两者不能互换。

## 7. 已验证与未完成

已验证：多模块 Java 全量测试、Python 混合检索测试、A2A 协议与跨租户访问测试、Java 到 Python 的本地端到端流程。

下一阶段：标准 OpenTelemetry Exporter、80 条人工复核评测集、Prompt 版本门禁、OIDC 联调和多实例任务租约。未完成项不会提前写成简历成果。
