package com.gaozhaoyang.agent.casework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "app.cases.a2a.enabled", havingValue = "true")
public class A2aEvidenceResearchGateway implements EvidenceResearchGateway {

    private final String endpoint;
    private final String issuer;
    private final String audience;
    private final JwtEncoder serviceTokenEncoder;
    private final ObjectMapper objectMapper;

    public A2aEvidenceResearchGateway(
            @Value("${app.cases.a2a.endpoint}") String endpoint,
            @Value("${app.cases.a2a.issuer}") String issuer,
            @Value("${app.cases.a2a.audience}") String audience,
            @Value("${app.cases.a2a.signing-secret}") String signingSecret,
            ObjectMapper objectMapper
    ) {
        if (signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("A2A_SIGNING_SECRET 至少需要 32 字节");
        }
        this.endpoint = endpoint;
        this.issuer = issuer;
        this.audience = audience;
        this.objectMapper = objectMapper;
        var key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.serviceTokenEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Override
    public ResearchResult research(
            String tenantId,
            CustomerSystemSnapshot system,
            String requirement,
            com.gaozhaoyang.agent.requirement.RequirementCard card,
            String existingRemoteTaskId
    ) {
        // The SDK default requests clear-text HTTP/2 (h2c). Uvicorn deliberately
        // serves HTTP/1.1 in the pilot, so create the official SDK transport with
        // an explicit HTTP/1.1 JDK client rather than falling back to custom JSON.
        JSONRPCTransport transport = createTransport();
        try {
            Task task;
            ClientCallContext callContext = callContext(tenantId);
            if (existingRemoteTaskId == null || existingRemoteTaskId.isBlank()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("systemId", system.systemId());
                payload.put("systemVersion", system.systemVersion());
                payload.put("requirement", requirement);
                payload.put("queries", evidenceQueries(requirement, card));
                Message message = Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .messageId(UUID.randomUUID().toString())
                        .parts(new TextPart(toJson(payload)))
                        .build();
                EventKind result = transport.sendMessage(
                        new MessageSendParams(message, null, Map.of(), tenantId), callContext);
                if (!(result instanceof Task returnedTask)) {
                    throw new IllegalStateException("知识 Agent 未返回 A2A Task");
                }
                task = returnedTask;
            } else {
                task = transport.getTask(new TaskQueryParams(existingRemoteTaskId, 0, tenantId), callContext);
            }
            return toResult(task);
        } finally {
            transport.close();
        }
    }

    @Override
    public void cancel(String tenantId, String remoteTaskId) {
        if (remoteTaskId == null || remoteTaskId.isBlank()) {
            return;
        }
        JSONRPCTransport transport = createTransport();
        try {
            transport.cancelTask(new CancelTaskParams(remoteTaskId, tenantId, Map.of()),
                    callContext(tenantId));
        } finally {
            transport.close();
        }
    }

    private JSONRPCTransport createTransport() {
        A2AHttpClient httpClient = forceHttp1(new JdkA2AHttpClient(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).build()));
        return new JSONRPCTransport(httpClient, null,
                new AgentInterface("JSONRPC", endpoint), List.of());
    }

    private ResearchResult toResult(Task task) {
        TaskState state = task.status().state();
        if (!state.isFinal()) {
            return new ResearchResult(task.id(), null, true);
        }
        if (state != TaskState.TASK_STATE_COMPLETED) {
            throw new IllegalStateException("知识 Agent 任务结束但未成功: " + state);
        }
        for (Artifact artifact : task.artifacts()) {
            if (!"EvidenceBundle".equals(artifact.name())) {
                continue;
            }
            for (org.a2aproject.sdk.spec.Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart) {
                    EvidenceBundle bundle = objectMapper.convertValue(dataPart.data(), EvidenceBundle.class);
                    return new ResearchResult(task.id(), bundle, false);
                }
            }
        }
        throw new IllegalStateException("知识 Agent 已完成，但没有 EvidenceBundle 产物");
    }

    private ClientCallContext callContext(String tenantId) {
        return new ClientCallContext(Map.of("tenant_id", tenantId), Map.of(
                "Authorization", "Bearer " + serviceToken(tenantId),
                "A2A-Version", "1.0",
                "traceparent", traceparent()
        ));
    }

    private String serviceToken(String tenantId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("java-case-orchestrator")
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(2, ChronoUnit.MINUTES))
                .claim("tenant_id", tenantId)
                .claim("scope", "knowledge:read")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return serviceTokenEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private List<String> evidenceQueries(String requirement,
                                         com.gaozhaoyang.agent.requirement.RequirementCard card) {
        LinkedHashMap<String, Boolean> queries = new LinkedHashMap<>();
        queries.put(requirement, true);
        card.affectedModules().stream().limit(2)
                .forEach(module -> queries.put(module + " 当前版本业务流程与影响范围", true));
        card.risks().stream().limit(2)
                .forEach(risk -> queries.put("如何验证和控制风险：" + risk, true));
        return queries.keySet().stream().limit(5).toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 A2A 证据调查请求", exception);
        }
    }

    private static String traceparent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId + "-" + spanId + "-01";
    }

    private static A2AHttpClient forceHttp1(JdkA2AHttpClient delegate) {
        return new A2AHttpClient() {
            @Override
            public GetBuilder createGet() {
                return delegate.createGet();
            }

            @Override
            public PostBuilder createPost() {
                PostBuilder target = delegate.createPost();
                // JSONRPCTransport asks for no redirects. The current SDK then
                // creates a second hard-coded HTTP/2 client, ignoring our supplied
                // HTTP/1.1 client. Keep redirects enabled so it uses the supplied
                // client; the endpoint is a fixed allow-listed configuration.
                target.followRedirects(true);
                return new PostBuilder() {
                    @Override public PostBuilder url(String url) { target.url(url); return this; }
                    @Override public PostBuilder addHeaders(Map<String, String> headers) { target.addHeaders(headers); return this; }
                    @Override public PostBuilder addHeader(String key, String value) { target.addHeader(key, value); return this; }
                    @Override public PostBuilder body(String body) { target.body(body); return this; }
                    @Override public PostBuilder followRedirects(boolean ignored) { target.followRedirects(true); return this; }
                    @Override public A2AHttpResponse post() throws IOException, InterruptedException { return target.post(); }
                    @Override public CompletableFuture<Void> postAsyncSSE(
                            Consumer<ServerSentEvent> eventConsumer,
                            Consumer<Throwable> errorConsumer,
                            Runnable complete) throws IOException, InterruptedException {
                        return target.postAsyncSSE(eventConsumer, errorConsumer, complete);
                    }
                };
            }

            @Override
            public DeleteBuilder createDelete() {
                return delegate.createDelete();
            }
        };
    }
}
