package com.gaozhaoyang.agent.casework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCaseRepository implements CaseRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCaseRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequirementCase insert(RequirementCase item, String idempotencyKey) {
        try {
            jdbc.update("""
                            INSERT INTO requirement_cases
                            (case_id, tenant_id, created_by, status, idempotency_key, remote_task_id,
                             snapshot_json, created_at, updated_at, version)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    item.caseId(), item.tenantId(), item.createdBy(), item.stage().name(),
                    blankToNull(idempotencyKey), item.remoteTaskId(), json(item),
                    Timestamp.from(item.createdAt()), Timestamp.from(item.updatedAt()));
            return item.storedAtVersion(0);
        } catch (DuplicateKeyException exception) {
            return findByTenantAndIdempotencyKey(item.tenantId(), idempotencyKey)
                    .orElseThrow(() -> exception);
        }
    }

    @Override
    public RequirementCase update(RequirementCase item) {
        long nextVersion = item.version() + 1;
        int changed = jdbc.update("""
                        UPDATE requirement_cases
                           SET status = ?, remote_task_id = ?, snapshot_json = ?,
                               updated_at = ?, version = ?
                         WHERE case_id = ? AND version = ?
                        """,
                item.stage().name(), item.remoteTaskId(), json(item),
                Timestamp.from(item.updatedAt()), nextVersion, item.caseId(), item.version());
        if (changed != 1) {
            throw new OptimisticLockingFailureException("Case 已被其他请求更新，请重试: " + item.caseId());
        }
        return item.storedAtVersion(nextVersion);
    }

    @Override
    public Optional<RequirementCase> findById(String caseId) {
        return queryOne("SELECT snapshot_json, version FROM requirement_cases WHERE case_id = ?", caseId);
    }

    @Override
    public Optional<RequirementCase> findByTenantAndId(String tenantId, String caseId) {
        return queryOne("""
                SELECT snapshot_json, version FROM requirement_cases
                 WHERE tenant_id = ? AND case_id = ?
                """, tenantId, caseId);
    }

    @Override
    public Optional<RequirementCase> findByTenantAndIdempotencyKey(String tenantId, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return queryOne("""
                SELECT snapshot_json, version FROM requirement_cases
                 WHERE tenant_id = ? AND idempotency_key = ?
                """, tenantId, key);
    }

    @Override
    public List<RequirementCase> findByTenant(String tenantId, int limit, int offset) {
        return jdbc.query("""
                        SELECT snapshot_json, version FROM requirement_cases
                         WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> read(rs), tenantId, limit, offset);
    }

    @Override
    public List<RequirementCase> findRecoverable(int limit) {
        return jdbc.query("""
                        SELECT snapshot_json, version FROM requirement_cases
                         WHERE status IN ('ANALYZING_REQUIREMENT', 'RESEARCHING_EVIDENCE', 'GENERATING_SOLUTION')
                         ORDER BY updated_at ASC LIMIT ?
                        """,
                (rs, rowNum) -> read(rs), limit);
    }

    @Override
    public void audit(String caseId, String tenantId, String actorId, String actorRole,
                      String action, Object details) {
        jdbc.update("""
                        INSERT INTO requirement_case_audit
                        (audit_id, case_id, tenant_id, actor_id, actor_role, action, details_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), caseId, tenantId, actorId, actorRole,
                action, json(details), Timestamp.from(Instant.now()));
    }

    private Optional<RequirementCase> queryOne(String sql, Object... args) {
        List<RequirementCase> items = jdbc.query(sql, (rs, rowNum) -> read(rs), args);
        return items.stream().findFirst();
    }

    private RequirementCase read(ResultSet rs) throws SQLException {
        try {
            RequirementCase item = objectMapper.readValue(rs.getString("snapshot_json"), RequirementCase.class);
            return item.storedAtVersion(rs.getLong("version"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("无法读取 Case 快照", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Case 数据", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
