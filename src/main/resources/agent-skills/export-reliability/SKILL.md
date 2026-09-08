---
name: export-reliability
description: 当需求涉及数据导出、下载、大数据量处理或异步任务时，设计可控的数据范围、流式处理、失败恢复与审计策略。
allowed-tools:
  - sandbox-read
metadata:
  phases: GENERATION
---

# Export Reliability

Keep export behavior explicit and bounded.

- Distinguish all records from the current filtered result and make the choice visible in the API.
- Prefer streaming or paged reads instead of loading an unbounded result into memory.
- Move large exports to an asynchronous job with observable status and a finite retention period.
- Validate requested fields and formats against an allowlist; never let user input become a raw query fragment.
- Cover authorization, boundary volume, failure status, and result consistency in tests.
- Record who requested the export and whether it succeeded without logging exported sensitive content.
