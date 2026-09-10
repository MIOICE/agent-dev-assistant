# 阶段 12：Claim–Evidence 语义审查

## 要解决的问题

阶段 11 已能证明“方案结论引用了哪个真实 Chunk”，但引用存在不代表内容真的支持结论。例如检索结果可能只是主题相关，也可能明确写着相反规则。本阶段增加独立 Critic，将来源追踪升级为可解释的语义审查信号。

## 执行链路

```text
TechnicalSolution
  -> Grounding 拆分原子结论并限制可引用 Chunk
  -> 去重 evidenceId 字典 + claimId 清单
  -> DeepSeek 一次批量结构化判断
  -> Java 确定性后校验
  -> SolutionCritiqueReport
  -> 工作流快照、Trace 与人工审批页面
```

每条结论会得到以下状态之一：

- `SUPPORTED`：合法证据明确支持结论；
- `CONTRADICTED`：合法证据与结论明确冲突；
- `INSUFFICIENT`：证据相关但不足，或模型判定没有合法引用；
- `ASSUMPTION`：方案已经显式标记为待确认假设；
- `NOT_EVALUATED`：模型没有返回该结论，或当前处于 Mock/降级模式。

## 为什么还需要 Java 后校验

结构化输出解决 JSON 形状，不保证模型引用的标识符真实存在。`SolutionCritiqueAssembler` 执行以下确定性约束：

- 只接受 Grounding 报告中存在的 claim ID；
- evidence ID 必须同时属于该结论允许的引用集合和最终证据集合；
- 每条判定最多保留 3 个去重引用；
- 未支撑的 Grounding 结论直接归为证据不足；
- 假设不能被模型改写成事实性支持；
- 模型漏项显式记录为未评估；
- 支持或矛盾但没有合法引用时自动降级。

这体现了生产 Agent 的基本分层：模型负责不确定的语义任务，普通程序负责可以确定验证的边界。

## 批量上下文设计

所有结论共享一份按 evidence ID 去重的证据字典，每条结论只携带允许引用的 ID。与逐条调用相比，这能减少重复 Chunk、降低网络往返并保持分类口径一致。当前方案规模受工作流限制；如果将来结论和证据超过 Token 预算，再按结论组分批执行。

## 降级与诚实边界

Mock 模式没有模型语义能力，因此不会用关键词规则伪装“支持”。已绑定证据的事实性结论统一显示 `NOT_EVALUATED`。DeepSeek 超时、返回非法 JSON 或 Schema 校验失败时同样保守降级，但不会丢失方案、引用和工作流状态。

`safeForApproval` 只表示当前 Critic 没有发现矛盾、不足或漏项，不代表结论绝对正确。最终审批始终由人完成。当前项目尚未构建 claim–evidence 人工金标集，不能声称 Critic 准确率。

## 验证结果

- 验证未知 claim ID 不进入最终报告；
- 验证越权 evidence ID 被剔除；
- 验证支持/矛盾判定没有合法引用时降级；
- 验证模型漏项变为 `NOT_EVALUATED`；
- 验证 Mock 模式不伪造 `SUPPORTED`；
- 验证方案首次生成和驳回重生成都会产生独立 Critic Span；
- 全项目 97 个自动化测试通过。

## 面试复述

> 我把“有引用”和“引用是否支持结论”拆成两个节点。Grounding 只建立结论到真实 Chunk 的可追溯关系，之后 Claim–Evidence Critic 用一次 DeepSeek 批量调用判断支持、矛盾或证据不足。模型返回后，Java 会再次校验 claimId、该结论允许的 evidenceId 和最终证据集合，未知 ID、漏项或无合法引用的肯定判定都会被忽略或降级。Mock 和模型失败时会诚实显示未评估，结果只是人工审批信号，不包装成事实证明。

## 简历可写版本

> 在方案 Grounding 后增加批量 Claim–Evidence Critic，将去重证据字典与原子结论一次性提交 DeepSeek，输出支持、矛盾和证据不足判定；通过 Java 校验 claimId、证据 ID 白名单及结果完整性，缺失或非法引用自动降级，模型不可用时保留未评估状态并进入人工审批；全项目累计完成 97 个自动化测试。
