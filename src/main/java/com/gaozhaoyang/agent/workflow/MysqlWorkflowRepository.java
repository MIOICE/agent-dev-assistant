package com.gaozhaoyang.agent.workflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        name = "app.workflow.repository",
        havingValue = "mysql"
)
public class MysqlWorkflowRepository implements WorkflowRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS workflow_state (
                workflow_id VARCHAR(36) PRIMARY KEY,
                stage VARCHAR(50) NOT NULL,
                state_json LONGTEXT NOT NULL,
                updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    ON UPDATE CURRENT_TIMESTAMP(6)
            )
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO workflow_state (
                workflow_id,
                stage,
                state_json,
                updated_at
            ) VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                stage = VALUES(stage),
                state_json = VALUES(state_json),
                updated_at = CURRENT_TIMESTAMP(6)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT state_json
            FROM workflow_state
            WHERE workflow_id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT state_json
            FROM workflow_state
            ORDER BY updated_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String FIND_ALL_BY_STAGE_SQL = """
            SELECT state_json
            FROM workflow_state
            WHERE stage = ?
            ORDER BY updated_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM workflow_state";

    private static final String COUNT_BY_STAGE_SQL =
            "SELECT COUNT(*) FROM workflow_state WHERE stage = ?";

    private final ObjectMapper objectMapper;
    private final String url;
    private final String username;
    private final String password;

    public MysqlWorkflowRepository(
            ObjectMapper objectMapper,
            @Value("${app.workflow.mysql.url}") String url,
            @Value("${app.workflow.mysql.username}") String username,
            @Value("${app.workflow.mysql.password}") String password
    ) {
        this.objectMapper = objectMapper;
        this.url = url;
        this.username = username;
        this.password = password;
        initializeTable();
    }

    @Override
    public WorkflowState save(WorkflowState state) {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)
        ) {
            statement.setString(1, state.workflowId());
            statement.setString(2, state.stage().name());
            statement.setString(3, objectMapper.writeValueAsString(state));
            statement.executeUpdate();
            return state;
        } catch (Exception exception) {
            throw new WorkflowPersistenceException(
                    "保存工作流失败：" + state.workflowId(),
                    exception
            );
        }
    }

    @Override
    public Optional<WorkflowState> findById(String workflowId) {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)
        ) {
            statement.setString(1, workflowId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(deserializeState(resultSet.getString("state_json")));
            }
        } catch (Exception exception) {
            throw new WorkflowPersistenceException(
                    "读取工作流失败：" + workflowId,
                    exception
            );
        }
    }

    @Override
    public List<WorkflowState> findAll(WorkflowStage stage, int offset, int limit) {
        String sql = stage == null ? FIND_ALL_SQL : FIND_ALL_BY_STAGE_SQL;
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            int parameterIndex = 1;
            if (stage != null) {
                statement.setString(parameterIndex++, stage.name());
            }
            statement.setInt(parameterIndex++, limit);
            statement.setInt(parameterIndex, offset);

            List<WorkflowState> states = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    states.add(deserializeState(resultSet.getString("state_json")));
                }
            }
            return List.copyOf(states);
        } catch (Exception exception) {
            throw new WorkflowPersistenceException("读取工作流列表失败", exception);
        }
    }

    @Override
    public long count(WorkflowStage stage) {
        String sql = stage == null ? COUNT_SQL : COUNT_BY_STAGE_SQL;
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            if (stage != null) {
                statement.setString(1, stage.name());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new WorkflowPersistenceException("统计工作流失败", exception);
        }
    }

    private void initializeTable() {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(CREATE_TABLE_SQL)
        ) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new WorkflowPersistenceException(
                    "初始化工作流数据表失败，请检查数据库地址、账号和密码",
                    exception
            );
        }
    }

    private WorkflowState deserializeState(String json) throws Exception {
        if (json.contains("\"revision\"")) {
            return objectMapper.readValue(json, WorkflowState.class);
        }

        // 兼容早期版本保存的七字段快照。工作流下一次保存时会自动升级为新结构。
        int closingBrace = json.lastIndexOf('}');
        if (closingBrace < 0) {
            throw new IllegalArgumentException("工作流快照不是合法JSON对象");
        }
        String now = Instant.now().toString();
        String migrationFields = """
                ,"solutionFeedbacks":[],
                "events":[{"eventId":"%s","type":"CREATED","message":"历史工作流已兼容迁移","occurredAt":"%s"}],
                "revision":1,
                "createdAt":"%s",
                "updatedAt":"%s",
                "failureMessage":null
                """.formatted(UUID.randomUUID(), now, now, now);
        String migratedJson = json.substring(0, closingBrace)
                + migrationFields
                + json.substring(closingBrace);
        return objectMapper.readValue(migratedJson, WorkflowState.class);
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
