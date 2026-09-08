package com.gaozhaoyang.agent.coding;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SandboxProjectTemplate {

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>demo.generated</groupId>
                <artifactId>agent-generated-patch</artifactId>
                <version>1.0.0</version>
                <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <junit.version>5.12.2</junit.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>${junit.version}</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>3.5.5</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    public void initialize(Path workspace) throws IOException {
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("pom.xml"), POM, StandardCharsets.UTF_8);
    }
}
