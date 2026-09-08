---
name: security-review
description: 当需求涉及输入校验、用户权限、敏感数据、删除写入或其他副作用时，对生成的 Java 代码执行安全复核。
allowed-tools:
  - sandbox-read
metadata:
  phases: GENERATION,REPAIR
---

# Security Review

Preserve the host application's least-privilege and human-approval boundaries.

- Validate untrusted inputs and reject invalid states explicitly.
- Keep authorization decisions visible in domain behavior and cover denial paths with tests.
- Avoid exposing secrets or sensitive values in source, errors, logs, or generated fixtures.
- Do not introduce network, filesystem, reflection, process, environment-variable, or database access.
- Skill metadata never grants a tool. Use only capabilities already authorized by the host Agent.
