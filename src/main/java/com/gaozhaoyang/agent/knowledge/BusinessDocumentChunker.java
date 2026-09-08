package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BusinessDocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile(
            "^(#{1,4})\\s+(.+)$"
    );

    private final TokenTextSplitter textSplitter =
            TokenTextSplitter.builder()
                    .withChunkSize(120)
                    .withMinChunkSizeChars(40)
                    .withMinChunkLengthToEmbed(10)
                    .withMaxNumChunks(100)
                    .withKeepSeparator(true)
                    .withPunctuationMarks(
                            List.of('。', '？', '！', '；', '\n')
                    )
                    .build();

    public List<Document> split(List<BusinessDocument> businessDocuments) {
        List<Document> chunks = new ArrayList<>();

        for (BusinessDocument businessDocument : businessDocuments) {
            int chunkIndex = 0;
            for (MarkdownSection section : splitByHeading(businessDocument)) {
                Document sourceDocument = toSpringAiDocument(
                        businessDocument,
                        section
                );
                List<Document> sourceChunks = textSplitter.split(sourceDocument);
                if (sourceChunks.isEmpty() && !section.content().isBlank()) {
                    sourceChunks = List.of(sourceDocument);
                }

                for (Document sourceChunk : sourceChunks) {
                    chunkIndex++;

                    Map<String, Object> metadata = new LinkedHashMap<>(
                            sourceChunk.getMetadata()
                    );
                    metadata.put("chunkIndex", chunkIndex);

                    chunks.add(new Document(
                            businessDocument.id() + "#chunk-" + chunkIndex,
                            sourceChunk.getText(),
                            metadata
                    ));
                }
            }
        }

        return List.copyOf(chunks);
    }

    private Document toSpringAiDocument(
            BusinessDocument businessDocument,
            MarkdownSection section
    ) {
        String retrievalText = "【%s｜%s｜%s】%n%s".formatted(
                businessDocument.businessModule(),
                businessDocument.title(),
                section.headingPath(),
                section.content()
        );
        return Document.builder()
                .id(businessDocument.id())
                .text(retrievalText)
                .metadata("sourceId", businessDocument.id())
                .metadata("title", businessDocument.title())
                .metadata("keywords", businessDocument.keywords())
                .metadata("sourceType", businessDocument.sourceType())
                .metadata("sourcePath", businessDocument.sourcePath())
                .metadata("businessModule", businessDocument.businessModule())
                .metadata("businessCategory", businessDocument.businessCategory())
                .metadata("documentType", businessDocument.documentType())
                .metadata("sanitized", businessDocument.sanitized())
                .metadata("headingPath", section.headingPath())
                .build();
    }

    private List<MarkdownSection> splitByHeading(
            BusinessDocument businessDocument
    ) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        String currentPath = businessDocument.title();

        for (String line : businessDocument.content().lines().toList()) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                addSection(sections, currentPath, currentLines);
                currentLines = new ArrayList<>();

                int level = matcher.group(1).length();
                String heading = matcher.group(2).trim();
                while (headingStack.size() >= level) {
                    headingStack.removeLast();
                }
                while (headingStack.size() < level - 1) {
                    headingStack.add(businessDocument.title());
                }
                headingStack.add(heading);
                currentPath = String.join(" > ", headingStack);
            }
            currentLines.add(line);
        }
        addSection(sections, currentPath, currentLines);

        if (sections.isEmpty()) {
            return List.of(new MarkdownSection(
                    businessDocument.title(),
                    businessDocument.content()
            ));
        }
        return sections;
    }

    private void addSection(
            List<MarkdownSection> sections,
            String headingPath,
            List<String> lines
    ) {
        String content = String.join(System.lineSeparator(), lines).trim();
        if (!content.isBlank()) {
            sections.add(new MarkdownSection(headingPath, content));
        }
    }

    private record MarkdownSection(String headingPath, String content) {
    }
}
