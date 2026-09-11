# 里程碑 18：端到端 Agent 可观测性

## 业务问题

需求工作流、MCP 工具、后台 Coding Agent 和 Docker 构建原本各自有日志或状态，出现异常时仍需要在多个页面和文件之间人工拼接。对于会多轮检索、调用工具、暂停审批并在后台恢复的 Agent，仅看最终成功或失败无法回答：慢在哪里、失败在哪一步、是否调用了工具、自动修复是否真的取得进展。

## 本阶段实现

- 使用 `workflowId` 作为端到端 `traceId`，统一关联需求分析、Agentic RAG、方案生成、工具调用、Coding Agent、Agent Loop 和沙箱构建。
- `AgentTraceContext` 只在同步调用链中传播关联 ID，并在嵌套调用或异常后恢复上下文；不保存 Prompt、业务正文和密钥。
- MCP/Spring AI 工具审计增加 `traceId`，只记录工具名、通道、输入输出长度、耗时和异常类型，不记录查询正文与返回正文。
- `AgentObservabilityService` 不复制运行状态，而是从持久化工作流快照、工具审计和 Coding Checkpoint 构建统一 Trace 投影。
- Coding Agent 使用 `taskId` 形成子树，Agent Loop 与每次沙箱构建作为子 Span；构建耗时使用真实测量值，只有事件时间戳的 Loop 步骤明确标记 `event_timestamp_only`。
- 前端展示 Span 分类、状态、耗时、Agent/RAG/工具/Loop/构建数量以及整体运行时间，并支持手动刷新。

接口：

```text
GET /api/observability/traces/{workflowId}
```

## 为什么不直接伪造 Token 和成本

当前 DeepSeek 结构化调用链没有在所有调用模式下稳定提供 Token Usage。项目将 `tokenUsageAvailable` 明确返回为 `false`，输入、输出 Token 置零并解释原因，不使用字符数估算后冒充真实 Token。后续接入稳定 Usage 元数据后，可以在同一摘要模型中增加真实 Token 和成本。

## 与 OpenTelemetry 的关系

统一模型采用 Trace、Span、parentSpanId、operation、status、start/end、duration 和 attributes 等通用概念，便于后续映射到 OpenTelemetry；本阶段实现的是项目内统一投影和页面展示，尚未接入 OpenTelemetry SDK、Collector、Tempo、Jaeger 或 Grafana，简历与面试中必须保持这个边界。

## 面试复述

> 这个 Agent 横跨同步需求工作流、模型工具调用、异步 Coding Agent 和 Docker 沙箱，单看应用日志很难还原一次任务。我使用 workflowId 作为端到端 traceId，把已有的工作流 Span、隐私化工具审计和 Coding Checkpoint 投影成统一 Trace；taskId 是编码子树，Loop 行动和每次构建作为子 Span。同步工具调用通过只携带关联 ID 的 Trace Context 归属到当前工作流，异步任务不依赖 ThreadLocal，而是使用持久化 workflowId 恢复关联。页面可以直接看到各类调用数量、失败点和耗时。拿不到可靠 Token Usage 时我明确标记不可用，没有用字符数伪造指标。

## 简历可写版本

> 面向跨越需求分析、Agentic RAG、Tool Calling、后台 Coding Agent 与 Docker 沙箱的长链路任务，设计以 `workflowId` 为关联键的端到端 Agent Trace，将工作流 Span、隐私化工具审计、Agent Loop 及构建 Checkpoint 汇总为统一可观测视图；记录节点状态、真实耗时、失败位置与调用统计，并通过安全 Trace Context 关联同步工具调用，避免保存 Prompt、密钥和业务正文。

## 验证结果

- `mvn test`：127 个自动化测试全部通过。
- 前端内嵌 JavaScript 通过 Node 语法检查。
- 独立端口 Mock 冒烟创建完整工作流后，统一 Trace 接口返回 5 个 Span，其中 4 个 Agent 节点、1 个 RAG 节点，状态为等待人工审批。
- 工具关联测试验证 Spring AI 通道审计能够按 `workflowId` 查询，同时审计对象不包含原始查询与返回正文。

## 当前边界

- 工具审计当前为单机内存环形记录，重启后不会保留。
- Coding Agent 的状态与构建记录来自文件 Checkpoint，工作流 Span 是否持久化取决于工作流仓库配置。
- 尚未接入标准 OpenTelemetry Exporter 和外部可观测平台。
- 尚未得到稳定的模型 Token Usage 与真实计费数据。
- Agent Loop 当前只有行动发生时间，不能伪装成精确模型执行耗时。

## 下一阶段

实现任务控制与生产级运行治理：增加用户取消、超时中止、幂等提交和并发配额，让长时间 Agent 不只“看得见”，还能够被安全控制。
