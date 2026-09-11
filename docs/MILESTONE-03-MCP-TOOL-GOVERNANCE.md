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

## 验证结果

- `mvn clean test`：56 个自动化测试全部通过。
- MCP `initialize`：HTTP 200，成功协商协议版本 `2025-06-18`。
- `tools/list`：发现 2 个工具，二者均返回只读、非破坏语义提示和输入 JSON Schema。
- `tools/call`：数据库元数据工具成功返回订单相关表结构。
- 非法空参数调用返回错误，并生成 `DENIED` 审计；正常调用生成 `SUCCESS` 审计。
- 前端新增 MCP 治理卡片，可查看协议入口、暴露边界、白名单、策略与最近审计。

## 下一阶段

进入安全代码工作区与补丁生成：让 Agent 在隔离的演示仓库中读取限定文件、生成统一 Diff、执行构建测试，并在人工批准前禁止写入主工作区。该阶段会验证 Sandbox、Autonomy Budget、Human-in-the-loop 和可回滚执行。
