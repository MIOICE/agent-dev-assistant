FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY case-orchestrator/pom.xml case-orchestrator/pom.xml
COPY coding-agent-archive/pom.xml coding-agent-archive/pom.xml
COPY case-orchestrator/src case-orchestrator/src
RUN mvn -q -pl case-orchestrator -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/case-orchestrator/target/case-orchestrator-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
