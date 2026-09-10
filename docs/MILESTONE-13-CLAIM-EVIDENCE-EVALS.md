# 阶段 13：Claim–Evidence 人工金标评测

## 要解决的问题

阶段 12 已实现语义审查，但“模型能返回分类”不能回答“分类效果怎么样”。普通 JUnit 只能验证代码分支和确定性约束，也不能代表模型语义准确率。

本阶段构建一套可直接调用生产 Critic 的离线评测链路，让提示词、模型版本或上下文格式变化可以得到可比较的结果。

## 评测集

`claim-evidence-cases.json` 包含 12 条人工标注的合成 MES 业务样例：

- 4 条 `SUPPORTED`：证据明确支持方案结论；
- 4 条 `CONTRADICTED`：证据明确写出相反约束；
- 4 条 `INSUFFICIENT`：证据主题相关，但无法推导具体保留天数、QPS、编码或缓存 TTL。

场景覆盖订单导出、权限、逻辑删除、状态机、性能和缓存。全部内容为合成数据，可以进入公开仓库，不包含真实 MES 文档或客户信息。

## 执行方式

```text
12 条金标样例
  -> 每条转换为一个 GroundedSolutionClaim 和唯一 Evidence
  -> 复用生产 SolutionEvidenceCritic 一次批量审查
  -> 按稳定 claimId 对齐预测与金标
  -> 计算 Coverage、Accuracy、分类 Recall、Macro Recall 和混淆矩阵
  -> API 与前端展示
```

接口：`GET /api/solution-critique/evaluation`

DeepSeek 模式会实际执行一次批量模型调用。Mock 模式不会伪造语义分类，12 条结果均为 `NOT_EVALUATED`，因此 Coverage 为 0。

## 指标定义

- `Coverage = 已产生有效三分类的样例数 / 全部样例数`；
- `Accuracy = 正确分类数 / 已产生有效三分类的样例数`；
- 分类 Recall：某个金标类别被正确识别的比例；
- Macro Recall：三个类别 Recall 的算术平均；
- 混淆矩阵：逐项展示每个预期类别被预测成什么。

`NOT_EVALUATED` 不进入 Accuracy 分母，但会降低 Coverage 与对应类别 Recall，避免漏答困难样例造成虚高准确率。

## 验证结果

- 验证资源文件可以读取 12 条均衡金标；
- 验证重复样例 ID 会使应用快速失败；
- 使用确定性预测桩验证 Coverage、Accuracy、三类 Recall、Macro Recall 和混淆矩阵公式；
- 验证 Mock 模式覆盖率为 0，而不是伪造准确率；
- 验证评测 API 返回完整结构；
- 验证前端可以触发并展示指标、混淆矩阵和逐题结果；
- 全项目 102 个自动化测试通过。

## 面试复述

> 我没有用 JUnit 通过率冒充模型效果，而是单独建立了 12 条人工标注的合成 Claim–Evidence 样例，支持、矛盾和证据不足各 4 条。评测直接复用生产 Critic，一次批量调用后计算 Coverage、Accuracy、各类别 Recall、Macro Recall 和混淆矩阵。模型漏项或降级结果记为 NOT_EVALUATED，会降低 Coverage 和 Recall，不能通过跳过困难题获得虚高 Accuracy。当前数据用于回归，不声称代表生产准确率。

## 简历可写版本

> 构建 Claim–Evidence 离线评测链路，设计 12 条均衡覆盖支持、矛盾与证据不足的合成业务金标样例，复用生产 Critic 执行批量评测并输出 Coverage、Accuracy、分类 Recall、Macro Recall 与混淆矩阵；将模型漏项和降级结果计入覆盖率与召回损失，避免只看已回答样例造成指标虚高。

## 当前边界

- 12 条样例规模较小，只适合快速回归和演示指标链路；
- 数据是合成业务案例，尚未经过真实业务专家复核；
- 当前没有固定模型版本的正式基线数据，不能写具体模型准确率；
- 下一步应建立经过授权的脱敏真实样例、双人标注规范、盲测集和版本对比记录。
