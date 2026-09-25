# Milestone 25：A2A 跨语言链路追踪

## 完成内容

- Java Case 状态机为需求分析、证据调查和方案生成建立阶段化 Observation。
- Java A2A 客户端调用建立真实 Span，并通过 W3C `traceparent` 传播当前上下文，不再生成无父子关系的随机 Trace ID。
- Python A2A 知识 Agent 提取远端父上下文，建立 server Span，并为每个混合检索问题建立子 Span。
- 双端均以 OTLP/HTTP 可选导出；本地默认关闭，避免依赖 Collector 才能启动。
- 增加诊断用 OpenTelemetry Collector 配置与敏感数据标签规范。
- Durable Case 每次后台推进独立成 Trace，跨人工等待、重启与重试通过持久化 `case.id` 关联，避免创建超长 Span。

## 解决的问题

过去只能分别查看 Java 与 Python 日志，A2A 超时后无法快速判断是网络、远端任务还是检索慢。现在一次 Case 的关键步骤共享同一 `traceId`，可以比较各阶段耗时、定位失败边界，并将人工驳回原因与链路数据结合分析。

## 面试复述

我没有只在日志里打印一个 workflowId，而是用 OpenTelemetry 建立跨服务 Trace。Java 用 Micrometer Observation 记录 Case 阶段，在 A2A client Span 中透传 W3C `traceparent`；Python 验证服务身份后提取父上下文，继续创建 A2A server Span 和混合检索子 Span。遥测只保留阶段、命中数、耗时和错误，不采集需求与知识正文。这样既能定位跨语言 Agent 链路瓶颈，又控制了可观测性本身的数据泄漏风险。
