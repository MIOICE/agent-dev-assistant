# 阶段 14：版本化 Evals 与回归门禁

## 要解决的问题

阶段 13 能计算 Claim–Evidence 指标，但单次结果没有保存，也无法回答：

- 本次用了哪个模型、Prompt 和数据集？
- 修改后相对上一个可靠版本是提升还是下降？
- Mock 或模型降级结果是否会被误当成可发布版本？
- 服务重启后能否追溯历史结论？

本阶段将一次评测建模为不可变 `Eval Run`，增加可审计元数据、持久化历史、通过基线和自动发布门禁。

## Eval Run 内容

每次正式评测记录：

- `runId` 与运行时间；
- AI 模式与模型 ID；
- Prompt 逻辑版本；
- 数据集逻辑版本；
- 根据全部金标内容计算的 SHA-256 指纹；
- 完整 Coverage、Accuracy、分类 Recall、Macro Recall、混淆矩阵与逐题结果；
- 门禁阈值、结论和失败原因；
- 使用的通过基线及各指标差值。

逻辑版本便于人理解，SHA-256 用来发现“版本名未改但数据内容已变”的情况。

## 发布门禁

默认绝对阈值：

- Coverage ≥ 95%；
- Accuracy ≥ 85%；
- Macro Recall ≥ 80%；
- 三个分类中最低 Recall ≥ 75%。

如果存在历史通过基线，Coverage、Accuracy、三个分类 Recall 或 Macro Recall 中任一项下降超过 8 个百分点，同样判定失败。阈值都可以通过环境变量修改。

门禁状态分为：

- `PASSED`：绝对阈值和基线回归检查都通过，可以作为下一次可靠基线；
- `FAILED`：产生了有效模型结果，但质量不足或相对基线明显退化；
- `NOT_EVALUATED`：Mock 或降级结果没有完成模型语义判定，不能发布且不能成为基线。

基线只选择最近一次 `PASSED` 运行。失败版本不会自动成为新基线，从而避免连续小幅退化掩盖累计质量下降。

## API 与持久化

```text
POST /api/solution-critique/evaluation/runs
  -> 执行真实评测
  -> 读取最近通过基线
  -> 计算差值与门禁
  -> 原子保存 Eval Run
  -> 返回完整结果

GET /api/solution-critique/evaluation/runs?limit=20
GET /api/solution-critique/evaluation/runs/latest
```

评测会消耗模型额度并产生持久化副作用，因此正式运行只使用 `POST`。兼容的 `GET /api/solution-critique/evaluation` 也只读取最近一次已保存结果，不会调用模型。文件仓库先写同目录临时文件，再用原子移动替换目标 JSON，避免进程中断留下半写结果。

默认目录为 `F:/agent-workspaces/evaluation-runs`，可以使用 `EVALUATION_CHECKPOINT_ROOT` 修改。测试和临时运行可以把 `EVALUATION_REPOSITORY` 设为 `memory`。

## 前端体验

业务知识库面板提供两个入口：

- “运行语义评测”：触发一次版本化运行，展示门禁、版本、指标、基线差值、混淆矩阵和逐题结果；
- “评测历史”：读取最近 20 次运行，不调用模型，可以打开任意一次查看详情。

## 验证

- 验证数据集 SHA-256 指纹格式；
- 验证绝对阈值通过和失败；
- 验证 Mock 为 `NOT_EVALUATED`；
- 验证相对通过基线的指标回退；
- 验证评测运行原子落盘和重载；
- 验证失败运行不会替代通过基线；
- 验证创建、历史与最近运行 API；
- 验证前端脚本、入口和展示逻辑；
- 全项目 111 个自动化测试通过。

## 当前边界

- 目前只有 12 条合成金标，适合功能验证和快速回归，不代表生产准确率；
- Eval Run 使用单机文件仓库，尚未接入数据库和多实例并发控制；
- Prompt 版本由配置显式维护，尚未自动计算 Prompt 内容摘要；
- 门禁已在应用层完成，还没有接入 GitHub Actions 或真实发布流水线；
- 最近通过基线自动选择，生产环境应增加人工批准、测试环境和业务域维度。
