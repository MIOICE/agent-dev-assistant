---
name: java-code-generation
description: 根据已审批技术方案生成受限的 Java 21 业务实现与 JUnit 5 测试，适用于代码生成阶段的基础编码任务。
allowed-tools:
  - sandbox-write
  - sandbox-test
metadata:
  phases: GENERATION
---

# Java Code Generation

Implement only behavior supported by the approved requirement card and technical plan.

- Prefer a small domain class with explicit validation and deterministic behavior.
- Add JUnit 5 tests for the main success path, important boundary values, and rejected inputs.
- Keep names aligned with the business language in the approved plan.
- Do not invent databases, external APIs, performance numbers, or production integration details.
- Respect the file, package, dependency, and side-effect constraints supplied by the host Agent.

Return complete source files that can be compiled together; do not return fragments or commentary.
