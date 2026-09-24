# Coding Agent Archive

该模块保存原有的受控代码生成、沙箱测试、失败修复和 Coding Skills 实现。

- 主应用不依赖此模块，也不会扫描或启动 `/api/coding-tasks`、交付包和编码技能接口。
- 历史代码、测试和 `SKILL.md` 原样保留，可通过 `mvn -pl coding-agent-archive test` 单独验证。
- 客户需求到实施方案的主流程不得调用本模块。

这个边界是产品设计的一部分：实施方案 Agent 只生成可交研发评审的方案，不自动修改客户系统。
