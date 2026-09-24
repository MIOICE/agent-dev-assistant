CREATE TABLE IF NOT EXISTS requirement_cases (
    case_id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(120),
    remote_task_id VARCHAR(120),
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_requirement_cases_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_requirement_cases_tenant_updated
    ON requirement_cases (tenant_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_requirement_cases_status
    ON requirement_cases (status);

CREATE TABLE IF NOT EXISTS requirement_case_audit (
    audit_id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    actor_role VARCHAR(40) NOT NULL,
    action VARCHAR(80) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_case_audit_case FOREIGN KEY (case_id) REFERENCES requirement_cases(case_id)
);

CREATE INDEX IF NOT EXISTS idx_case_audit_case_created
    ON requirement_case_audit (case_id, created_at);
