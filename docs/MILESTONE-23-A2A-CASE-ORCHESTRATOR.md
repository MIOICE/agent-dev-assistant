# Milestone 23：实施方案 Case 与 A2A 知识协作

## 背景

旧版本把需求分析、知识检索和代码生成放在同一个 Java 应用中，演示能力较多，但业务主线不够清晰，也无法体现独立 Agent 之间的协议协作和客户版本隔离。

本阶段把主产品收敛为实施人员内部使用的“需求到方案”闭环，并将历史 Coding Agent 隔离到独立模块。

## 完成内容

- 根工程改为 Maven 聚合项目，拆分 `case-orchestrator` 与 `coding-agent-archive`。
- 新建异步可恢复 Case 状态机、JDBC 快照仓库、Flyway 迁移、幂等创建和乐观锁推进。
- 新建实施人员工作台，不提供客户登录入口。
- 使用官方 Java/Python A2A SDK 建立跨项目任务委派，产物为结构化 EvidenceBundle。
- A2A 使用限时服务 JWT，Python 独立校验客户范围；Case API 从用户 JWT 取得 tenant。
- 为知识空间增加客户、系统和版本映射，未登记版本拒绝检索。
- 将检索升级为语义 + 关键词 + RRF，并保存分路分数和覆盖缺口。
- Case 取消会通过 A2A CancelTask 尝试终止远端调查；即使远端不可达，本地状态边界仍阻止后续产物进入方案。
- 增加证据提示注入隔离、人工修订、驳回、批准和客户沟通稿。
- 新建需求澄清与方案证据检查 Skills，按阶段渐进加载并用 SHA-256 清单校验；自动生成的方案修订记录 Prompt 版本与 Skill 清单哈希。
- 新增 pilot Profile，强制 MySQL、OIDC 与真实 A2A。
- 旧工作流默认只读，旧 Coding Agent 不随主应用启动。

## 验证结果

- Maven 聚合工程全量测试通过。
- Python 混合检索测试与 A2A 租户边界测试通过。
- 本地完成 Java → Python → 知识空间 → EvidenceBundle → 方案 → 人工批准的端到端验证。

## 下一阶段

1. 接入标准 OpenTelemetry SDK/Collector，统一 Java/Python Trace。
2. 建设 80 条人工审核的检索与方案评测集，加入版本化 Eval Gate。
3. 与真实 OIDC 测试租户联调，并验证多实例恢复与任务租约。
