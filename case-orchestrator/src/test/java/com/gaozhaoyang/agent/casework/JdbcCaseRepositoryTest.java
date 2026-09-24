package com.gaozhaoyang.agent.casework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCaseRepositoryTest {

    private DataSource dataSource;
    private JdbcCaseRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:cases-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V1__create_requirement_cases.sql")).execute(dataSource);
        repository = new JdbcCaseRepository(new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void shouldIsolateTenantsAndSurviveRepositoryRecreation() {
        RequirementCase saved = repository.insert(newCase("tenant-a"), "request-1");

        assertThat(repository.findByTenantAndId("tenant-b", saved.caseId())).isEmpty();

        JdbcCaseRepository afterRestart = new JdbcCaseRepository(new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());
        RequirementCase restored = afterRestart.findByTenantAndId("tenant-a", saved.caseId()).orElseThrow();

        assertThat(restored.requirement()).isEqualTo("订单列表增加导出功能");
        assertThat(restored.systemSnapshot().systemVersion()).isEqualTo("v13.1");
        assertThat(restored.version()).isZero();
    }

    @Test
    void shouldReturnExistingCaseForSameTenantIdempotencyKey() {
        RequirementCase first = repository.insert(newCase("tenant-a"), "same-request");
        RequirementCase second = repository.insert(newCase("tenant-a"), "same-request");

        assertThat(second.caseId()).isEqualTo(first.caseId());
        assertThat(repository.findByTenant("tenant-a", 10, 0)).hasSize(1);
    }

    private RequirementCase newCase(String tenant) {
        return RequirementCase.create(java.util.UUID.randomUUID().toString(), tenant, "user-1",
                "订单列表增加导出功能",
                new CustomerSystemSnapshot("mes", "v13.1", "订单管理"), Instant.now());
    }
}
