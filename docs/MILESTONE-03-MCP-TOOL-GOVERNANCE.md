# 里程碑 03：标准 MCP Server 与工具治理

## 本阶段解决了什么

此前的 `@Tool` 只能由当前 Java 进程里的 Spring AI 调用。现在项目新增标准 MCP Server，外部支持 MCP 的 Agent 客户端可以发现并调用企业工具，而不需要了解 Java 类名或自行适配 REST 返回值。

MCP 入口默认是 `http://127.0.0.1:8080/api/mcp`，传输方式为 Streamable HTTP。当前显式开放两个只读工具：

- `search_business_document`：检索企业业务规范与来源。
- `query_database_metadata`：查询演示数据库表、字段和索引，不读取业务行数据。

## 一次调用是怎样发生的

```text
外部 Agent 客户端
  -> initialize：协商协议版本和能力
  -> tools/list：获取工具名、描述、JSON Schema 和安全提示
  -> tools/call：提交结构化参数
  -> ToolGovernanceService：白名单与参数校验
  -> 只读业务工具：执行检索或元数据查询
  -> 返回 MCP 内容，并写入隐私化审计
```

工具元数据明确声明：`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`、`openWorldHint=false`。这些提示帮助客户端做调度判断，但真正的安全不能只依赖描述，所以服务端仍执行白名单和参数校验。

## 治理策略

- 关闭本地 `@Tool` 自动转换，只有显式 `@McpTool` 的工具才会暴露。
- 查询参数必须非空且不超过 500 字。
- 当前只允许两个只读工具，不提供任意 SQL、文件写入或系统命令。
- Spring AI 单轮最多调用 8 次工具、单个工具最多调用 4 次，防止失控循环。
- 审计只保存工具名、通道、字符数、耗时、状态和异常类型，不保存 Prompt、完整查询、结果或密钥。
- 服务默认绑定 `127.0.0.1`。因为当前没有实现 MCP 用户认证，不应直接暴露到公网。
- 审计队列最多保留 500 条，属于演示级有界内存实现。

## 面试时怎么复述

> 项目原来只有 Spring AI 进程内 Tool Calling，耦合在单个应用里。我增加了 MCP Streamable HTTP Server，把业务规范检索和数据库元数据查询变成外部 Agent 可发现、可按 JSON Schema 调用的标准工具。为了避免“接了 MCP 就等于安全”的误区，我关闭自动暴露，只开放显式白名单工具，并增加参数长度校验、单轮调用上限、只读与幂等语义、默认本机监听和隐私化调用审计。当前 MCP 面向外部客户端，应用内部模型仍走进程内调用，避免无意义的网络回环。

如果追问 MCP 和 Function Calling 的区别：Function Calling 主要描述模型在一次推理中如何输出工具调用；MCP 解决客户端与外部工具服务之间怎样发现能力、协商协议和交换调用结果。项目同时保留两者：内部低延迟调用用 Spring AI `@Tool`，跨进程复用用 MCP。

如果追问安全边界：工具描述中的只读提示只是声明，不能替代服务端强制。当前真正强制的是“只注册两个工具 + 白名单校验 + 参数校验 + 不提供写接口 + 本机监听”。生产环境还需要 OAuth、租户身份、持久化审计与网关限流。

## 验证结果

- `mvn clean test`：56 个自动化测试全部通过。
- MCP `initialize`：HTTP 200，成功协商协议版本 `2025-06-18`。
- `tools/list`：发现 2 个工具，二者均返回只读、非破坏语义提示和输入 JSON Schema。
- `tools/call`：数据库元数据工具成功返回订单相关表结构。
- 非法空参数调用返回错误，并生成 `DENIED` 审计；正常调用生成 `SUCCESS` 审计。
- 前端新增 MCP 治理卡片，可查看协议入口、暴露边界、白名单、策略与最近审计。

## 本轮简历可写版本

项目名称：面向企业存量系统的需求开发 Agent

- 基于 Java 21、Spring Boot、Spring AI 与 DeepSeek 构建可恢复的 Agentic Workflow，串联结构化需求分析、本地 BGE RAG、技术方案生成和 Human-in-the-loop 审批，并使用 MySQL 保存工作流快照与事件历史。
- 将业务规范检索和数据库元数据查询封装为 MCP Streamable HTTP 工具，为外部 Agent 提供工具发现、输入 JSON Schema 与标准化调用能力；保留进程内 Tool Calling，按调用边界选择集成方式。
- 设计工具治理层，仅显式开放 2 个只读白名单工具，增加参数校验、单轮调用次数限制、只读/非破坏/幂等语义及本机暴露边界，并对成功、拒绝和异常调用进行隐私化审计。
- 建立结构化澄清、RAG 检索评测和 Agent Trace 看板，统计首轮就绪率、澄清轮数、推荐采纳率、完成率及节点耗时；累计完成 56 个 JUnit 5、MockMvc 与 H2/MySQL 兼容自动化测试。

## 下一阶段

进入安全代码工作区与补丁生成：让 Agent 在隔离的演示仓库中读取限定文件、生成统一 Diff、执行构建测试，并在人工批准前禁止写入主工作区。该阶段会验证 Sandbox、Autonomy Budget、Human-in-the-loop 和可回滚执行。
