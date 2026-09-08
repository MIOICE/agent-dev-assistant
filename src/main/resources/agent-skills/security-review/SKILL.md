---
name: security-review
description: Apply a focused security review to generated Java code when inputs, authorization, sensitive data, or side effects need explicit boundaries.
allowed-tools:
  - sandbox-read
metadata:
  phase: generation-and-repair
---

# Security Review

Preserve the host application's least-privilege and human-approval boundaries.

- Validate untrusted inputs and reject invalid states explicitly.
- Keep authorization decisions visible in domain behavior and cover denial paths with tests.
- Avoid exposing secrets or sensitive values in source, errors, logs, or generated fixtures.
- Do not introduce network, filesystem, reflection, process, environment-variable, or database access.
- Skill metadata never grants a tool. Use only capabilities already authorized by the host Agent.
