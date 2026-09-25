# 跨 Java/Python 链路观测

## 目标

一次 Case 会跨越 Java 编排服务和 Python 知识 Agent。只看两份应用日志，很难判断耗时发生在需求分析、A2A 网络、混合检索还是方案生成。本项目使用 OpenTelemetry 的 W3C Trace Context 和 OTLP，把一次后台推进中的步骤关联成同一条 Trace。

```text
case.process
├─ case.requirement.analyze
├─ case.evidence.research
│  └─ a2a.knowledge.research (Java client)
│     └─ a2a.knowledge.research (Python server，同一 traceId)
│        ├─ knowledge.hybrid_search
│        ├─ knowledge.hybrid_search
│        └─ ...
└─ case.solution.generate
```

## Java 端

- Spring Boot 使用 Micrometer Observation 创建 Case 阶段 Span，由 OpenTelemetry bridge 承载。
- A2A 调用建立独立 client Span，并从当前真实 Span 生成 W3C `traceparent`；没有活动 Span 时不伪造关联 ID。
- OTLP 导出默认关闭。本地没有 Collector 时仍可正常运行，设置 `OTEL_EXPORT_ENABLED=true` 后才发送 Trace。
- Durable Case 可能暂停等待人工澄清或审批，系统不会让 Span 跨小时保持打开；每次恢复推进创建新 Trace，并用持久化 `case.id` 关联多次执行。

## Python 端

- A2A 请求先完成 JWT 与租户范围校验，再解析 Java 发送的 `traceparent`。
- `a2a.knowledge.research` 作为 server Span 接续父链路，每个检索问题产生一个 `knowledge.hybrid_search` 子 Span。
- Span 只记录任务 ID、检索序号、Top-K、命中数和缺口数，不记录查询原文、文档正文、Prompt、`tenant_id` 或凭证。

Python 服务需要相同的三个环境变量：

```dotenv
OTEL_EXPORT_ENABLED=true
OTEL_SERVICE_NAME=ai-platform-knowledge-a2a
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:4318/v1/traces
```

## 本地验证

先启动只输出 Trace 到控制台的诊断 Collector：

```text
docker compose --profile observability up otel-collector
```

然后为 Java 和 Python 设置 `OTEL_EXPORT_ENABLED=true`，按 README 启动两个服务并执行一个 Case。Collector 日志中应出现相同 `traceId` 的 Java A2A client Span 与 Python A2A server Span。

当前 Collector 使用 `debug` exporter，只用于验证链路，不是生产存储。试点部署时再将 exporter 指向团队已有的 Jaeger、Tempo 或其他 OTLP 后端。

## 排障顺序

1. Java 有 Case Span、没有 A2A client Span：检查 Case 是否进入 `RESEARCHING_EVIDENCE`。
2. Java 有 client Span、Python 没有 server Span：检查 A2A 地址、JWT、网络与 `traceparent` 请求头。
3. Python 有 server Span、没有检索子 Span：检查客户、系统和版本是否映射到知识空间。
4. 检索子 Span 命中为 0：结合 EvidenceBundle 的 coverage、语义分与关键词分排查召回链路。
5. 不要通过把需求或 Chunk 正文写入 Span 来“方便排障”；正文仍应留在受权限控制的 Case 与证据存储中。

## 参考

- Spring Boot Tracing：<https://docs.spring.io/spring-boot/reference/actuator/tracing.html>
- OpenTelemetry Python：<https://opentelemetry.io/docs/languages/python/>
- W3C Trace Context：<https://www.w3.org/TR/trace-context/>
