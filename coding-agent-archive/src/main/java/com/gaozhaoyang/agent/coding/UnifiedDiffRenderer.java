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

    public String renderReplacement(String relativePath, String previous, String current) {
        String[] oldLines = normalizedLines(previous);
        String[] newLines = normalizedLines(current);
        StringBuilder diff = new StringBuilder()
                .append("--- a/").append(relativePath).append('\n')
                .append("+++ b/").append(relativePath).append('\n')
                .append("@@ -1,").append(effectiveLength(oldLines))
                .append(" +1,").append(effectiveLength(newLines)).append(" @@\n");
        appendLines(diff, oldLines, '-');
        appendLines(diff, newLines, '+');
        return diff.toString();
    }

    private String[] normalizedLines(String content) {
        return content.replace("\r\n", "\n").split("\n", -1);
    }

    private int effectiveLength(String[] lines) {
        return lines.length > 0 && lines[lines.length - 1].isEmpty()
                ? lines.length - 1
                : lines.length;
    }

    private void appendLines(StringBuilder diff, String[] lines, char prefix) {
        int length = effectiveLength(lines);
        for (int index = 0; index < length; index++) {
            diff.append(prefix).append(lines[index]).append('\n');
        }
    }
}
