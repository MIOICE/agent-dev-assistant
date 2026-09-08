package com.gaozhaoyang.agent.coding;

import org.springframework.stereotype.Component;

@Component
public class UnifiedDiffRenderer {

    public String renderAddedFile(String relativePath, String content) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        int lineCount = lines.length;
        if (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        StringBuilder diff = new StringBuilder()
                .append("--- /dev/null\n")
                .append("+++ b/").append(relativePath).append('\n')
                .append("@@ -0,0 +1,").append(lineCount).append(" @@\n");
        for (int index = 0; index < lineCount; index++) {
            diff.append('+').append(lines[index]).append('\n');
        }
        return diff.toString();
    }
}
