# 阶段 21：独立交付包验真

## 业务问题

交付端计算 SHA-256 只能说明“系统当时生成了什么”。接收方还需要在另一个入口确认下载、转发或归档后的 ZIP 是否完整，并且不能为了验证而直接解压到磁盘或执行模型生成的代码。

本阶段增加纯读取验真器。它在内存中受限解析 ZIP，将容器安全、来源清单和可信摘要检查集中成一份机器可读报告。

## 两层结论

1. **包内一致性**：逐项复算 Manifest 声明的字节数和 SHA-256，并要求实际内容集合完全相等。
2. **外部摘要一致性**：用户可粘贴下载接口返回或可信渠道保存的整包 SHA-256，验真器使用常量时间比较确认整个 ZIP 未变化。

未提供外部摘要时，验真成功仍会返回警告：攻击者如果能同时替换内容和 Manifest，普通哈希无法证明发布者身份。当前功能不宣称等同数字签名。

## 安全解析预算

- 压缩文件最多 3MB；
- 解压后总量最多 4MB；
- 单个条目最多 2MB；
- 最多 64 个条目；
- Manifest 最多 256KB；
- 不落盘、不执行代码；
- 拒绝绝对路径、Windows 盘符、反斜杠、`..`、未知顶层目录、目录占位和重复条目。

这些约束同时降低 Zip Slip、Zip Bomb、重复覆盖和无界内存占用风险。

## Manifest v2

`agent-delivery-v2` 在原有项目文件清单外增加 `contents`，覆盖：

- `APPLYING.md`；
- `changes/approved.patch`；
- `evidence/build-summary.txt`；
- `project/` 下全部文件。

验真时实际 ZIP 条目集合必须与 `contents` 完全一致。构建摘要还会单独与 `verification.outputSummarySha256` 交叉校验，项目目录则与 `files` 清单再次比对。

## API 与工作台

```text
POST /api/delivery-artifacts/verify
Content-Type: multipart/form-data
file=<delivery.zip>
expectedSha256=<可选的64位十六进制摘要>
```

工作台新增“交付包独立验真”区域，可以选择 ZIP、粘贴可信摘要并查看任务身份、Schema、条目数量、实际摘要、警告与错误。

## 验证范围

测试覆盖原始交付包通过、外部摘要匹配、内容被修改、Zip Slip 路径、错误可信摘要以及 multipart 控制器参数转交。全项目累计通过 142 个自动化测试。

## 当前边界

- 当前验证的是完整性和内部来源关系，不是组织身份签名。
- 尚未使用 Ed25519、KMS、Sigstore、in-toto Attestation 或透明日志。
- 验真器与生成器位于同一应用代码库；更高信任等级应提供独立 CLI/服务并固定受信任公钥。
