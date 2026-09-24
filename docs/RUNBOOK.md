# 开发与运行手册

## 环境

- JDK 21、Maven 3.9+
- Python 3.11+ 与 ai-platform `.venv`
- 本地演示可用 H2；试点必须使用 MySQL 8+
- DeepSeek 可选；Mock 模式可验证控制流但不代表模型效果

## 启动顺序

1. 在 ai-platform 配置同一 `A2A_SIGNING_SECRET`，启动端口 8020 的知识 Agent。
2. 在 Java 项目配置 A2A endpoint 与相同密钥。
3. 启动 `case-orchestrator`，打开 `http://localhost:8080/`。
4. 本地点击“进入工作台”获得开发 JWT；试点环境必须由 OIDC 提供令牌。

## 健康检查

```text
GET http://127.0.0.1:8020/.well-known/agent-card.json
GET http://127.0.0.1:8080/
```

Agent Card 应声明 `JSONRPC`、协议版本 `1.0` 和 `mes-evidence-research` 能力。

## 常见故障

### Case 停在 EVIDENCE_INSUFFICIENT

依次检查：客户版本是否登记、知识空间是否有文档、查询是否命中、证据版本是否一致、文档是否触发提示注入隔离。不要通过降低所有阈值让流程“看起来成功”。

### A2A 返回 401/403

检查两端密钥、issuer、audience、系统时钟、两分钟有效期、`knowledge:read` scope 和 `tenant_id`。

### A2A Task 已创建但 Java 重启

Case 中的 `remoteTaskId` 会被保留；恢复调度应执行 GetTask 而不是 SendMessage。若远端任务已失败，Case 进入 FAILED 并保留安全错误信息。

### Python 测试提示 --browser-channel 不识别

只运行后端测试时覆盖仓库的浏览器 addopts：

```text
python -m pytest -o addopts="" tests/test_hybrid_retrieval.py tests/test_knowledge_a2a.py -q
```

## 发布前检查

- `mvn test` 全部通过；
- Python 检索与 A2A 测试通过；
- `pilot` Profile 不含 H2 和开发令牌回退；
- OIDC 令牌包含受控的 roles 与 tenant_id；
- 两个试点客户互相查询 Case 和远端 Task 均失败；
- 证据不足案例不能生成客户沟通稿；
- 密钥、客户资料、简历和面试材料均未进入 Git。
