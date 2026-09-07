package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BusinessDocumentChunker {

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
            Document sourceDocument = toSpringAiDocument(businessDocument);
            List<Document> sourceChunks = textSplitter.split(sourceDocument);

            for (int index = 0; index < sourceChunks.size(); index++) {
                Document sourceChunk = sourceChunks.get(index);
                int chunkIndex = index + 1;

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

        return List.copyOf(chunks);
    }

    private Document toSpringAiDocument(
            BusinessDocument businessDocument
    ) {
        return Document.builder()
                .id(businessDocument.id())
                .text(businessDocument.content())
                .metadata("sourceId", businessDocument.id())
                .metadata("title", businessDocument.title())
                .metadata("keywords", businessDocument.keywords())
                .build();
    }
}
