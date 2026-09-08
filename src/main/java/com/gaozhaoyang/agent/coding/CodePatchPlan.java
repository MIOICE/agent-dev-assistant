package com.gaozhaoyang.agent.coding;

import java.util.List;

public record CodePatchPlan(
        String summary,
        List<GeneratedFile> files
) {
    public CodePatchPlan {
        summary = summary == null ? "" : summary.trim();
        files = files == null ? List.of() : List.copyOf(files);
    }
}
