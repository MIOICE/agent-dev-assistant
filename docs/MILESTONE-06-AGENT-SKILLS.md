# 里程碑 06：Agent Skills 与渐进式上下文加载

> 后续里程碑 07 已增加全文 SHA-256 启动校验与 BGE 语义路由。启动读取完整字节仅用于完整性计算，正文仍只在命中后进入模型上下文。

## 本阶段解决了什么

此前代码生成与失败修复的全部方法都写在固定 Prompt 中。随着功能增加，Prompt 会越来越长，不同阶段也会携带大量无关规则。本阶段把可复用的方法封装成标准 `SKILL.md` 技能包，并按执行阶段加载：

```text
应用启动
  -> 只索引 name / description / allowed-tools
  -> GENERATION：加载 java-code-generation + security-review 正文
  -> Docker 测试通过：不加载 repair 技能
  -> Docker 测试失败
  -> REPAIR：加载 test-failure-repair + security-review 正文
  -> 激活记录写入 CodingTask Checkpoint 与前端时间线
```

这就是 Progressive Disclosure：发现阶段只保留低成本元数据，真正命中任务后才把详细指令加入模型上下文。

## 三个技能

- `java-code-generation`：从已审批方案生成小型 Java 21 实现和 JUnit 5 测试。
- `test-failure-repair`：把沙箱日志当作不可信证据，做最小范围修复，不删除测试或放宽断言。
- `security-review`：检查输入验证、权限拒绝路径、敏感信息和危险副作用。

每个技能都使用 YAML frontmatter，至少包含 `name` 与 `description`；正文只保留会改变 Agent 决策的约束。三个技能均通过本地 `skill-creator` 校验器。

## 实现设计

`AgentSkillCatalog` 使用 classpath pattern 发现技能。建立目录时只读取 frontmatter，保留资源句柄；`activate` 时才读取正文，并通过 12,000 字符单技能上限和 24,000 字符阶段上下文上限防止上下文无界增长。重复技能名、非法命名、空描述、空正文和超长正文会被拒绝。

`StandardCodingSkillProvider` 将确定性阶段映射到技能集合。生成器与修复器收到 `SkillActivation`，把受信任的项目内技能正文放入单独的 Prompt 区域。任务快照保存已激活技能名称，事件时间线保存激活阶段；`GET /api/skills` 只返回元数据和进程内激活次数，不泄露完整指令。

## 安全边界

`allowed-tools` 只表达技能希望使用的能力，不会创建 Bean、注册 Tool Callback 或修改沙箱策略。权限仍来自：

- MCP 服务端显式白名单与参数校验；
- 代码文件路径、扩展名、危险 API、文件数与字节预算；
- 固定 Docker 命令、无网络和只读挂载；
- 技术方案审批与代码发布审批。

因此技能可以指导“怎么做”，不能决定“被允许做什么”。

## 本轮验证

- 三个 `SKILL.md` 均通过 `quick_validate.py`。
- 验证目录索引时激活次数为 0，且 API 不返回正文。
- 验证生成阶段只加载生成与安全技能，不加载修复技能。
- 验证构建失败进入修复阶段后，Checkpoint 增加修复技能且不重复记录安全技能。
- 累计 71 个自动化测试全部通过。
- 前端 JavaScript 独立语法检查通过。

## 当前真实边界

- 当前路由由确定性工作流阶段决定，还没有实现基于语义的动态技能检索或技能版本选择。
- 技能来自项目 classpath，属于受信任的随应用发布资产；尚未实现第三方技能签名、供应链扫描和租户级技能目录。
- 激活次数是进程内观测数据，任务级技能列表会持久化，但全局计数重启后归零。
- 技能正文进入模型上下文，不能替代代码策略、沙箱和人工审批等真正的安全边界。
