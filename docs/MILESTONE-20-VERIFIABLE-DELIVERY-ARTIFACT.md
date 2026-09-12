# 阶段 20：可验证代码交付包

## 目标

代码任务在 Docker 沙箱通过测试并完成人工审批后，原有实现只把文件复制到本机批准目录。这个状态适合隔离执行，但不便于跨人员交接、自动校验或归档。

本阶段增加一个只读交付层：从已发布 Checkpoint 和批准文件按需派生确定性 ZIP，并提供机器可读来源清单。交付层不修改任务、不执行代码，也不自动接触真实仓库。

## 包结构

```text
coding-delivery-{taskId}.zip
├─ APPLYING.md
├─ delivery-manifest.json
├─ changes/
│  └─ approved.patch
├─ evidence/
│  └─ build-summary.txt
└─ project/
   ├─ pom.xml
   ├─ src/main/...
   └─ src/test/...
```

`delivery-manifest.json` 使用 `agent-delivery-v1` Schema，记录：

- `taskId`、`workflowId`、工作流版本和发布时间；
- 构建命令、退出码、耗时和构建摘要 SHA-256；
- 自治预算、已执行 Agent 步骤、构建次数和修复轮数；
- 已激活 Skills；
- 每个工程文件的路径、操作类型、字节数和 SHA-256。

## 下载前验证

交付服务只接受 `PUBLISHED` 且最近构建通过的任务，并依次执行：

1. 批准路径必须精确等于配置根目录下的 `{taskId}`，不能引用其他目录；
2. 每个文件必须是普通文件，不能是符号链接；
3. 补丁文件的实际字节数和 SHA-256 必须与审批 Checkpoint 一致；
4. ZIP 所有源内容累计不得超过 2MB；
5. 任一检查失败时拒绝生成，不返回部分交付物。

这使“测试后、下载前被修改”的文件无法静默进入交付包。

## 确定性派生

ZIP 条目先放入按名称排序的集合，再统一写入固定时间戳。清单时间来自已发布任务的 `updatedAt`，不使用当前下载时间。因此在同一 Java 运行环境中，相同已发布快照能够派生相同 ZIP 字节和相同 SHA-256。

整个 ZIP 的 SHA-256 通过 `X-Artifact-SHA256` 响应头返回，元数据接口也返回相同摘要。清单不把 ZIP 摘要写回自身，从而避免递归哈希问题。

## API

```text
GET /api/coding-tasks/{taskId}/delivery/metadata
GET /api/coding-tasks/{taskId}/delivery
```

元数据接口用于工作台预览文件数、Schema、包大小和摘要；下载接口返回 `application/zip` 与附件文件名。

## 工作台

任务进入 `PUBLISHED` 后，页面展示：

- 交付包文件名与大小；
- 清单中的工程文件数；
- Schema 版本；
- 完整 ZIP SHA-256；
- 下载按钮。

其他任务阶段不显示交付区域，直接访问接口也会被服务端拒绝。

## 验证范围

自动化测试覆盖确定性构建、ZIP 条目、清单内容、完整包摘要、审批后 Java 文件与构建描述篡改、部分发布防护、未发布下载拒绝和 HTTP 下载响应头。全项目当前通过 138 个自动化测试。

## 当前边界

- 该清单是项目内来源记录，不宣称符合完整 SLSA Attestation 或 in-toto 标准。
- ZIP 摘要由服务返回，尚未使用组织私钥进行数字签名。
- 交付工程仍需维护者在目标仓库复核、重新测试和执行正式发布流程。
- 当前为内存派生，2MB 上限适合受控代码补丁；大型产物应改为流式生成和对象存储。
