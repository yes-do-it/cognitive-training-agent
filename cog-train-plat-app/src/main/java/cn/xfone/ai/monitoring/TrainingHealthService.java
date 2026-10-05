package cn.xfone.ai.monitoring;

import cn.xfone.ai.api.training.TrainingHealthComponentResponse;
import cn.xfone.ai.api.training.TrainingHealthResponse;
import cn.xfone.ai.agent.TrainingAgentService;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingHealthService {
    private final ObjectProvider<DataSource> mysqlDataSourceProvider;
    private final ObjectProvider<JdbcTemplate> pgVectorJdbcTemplateProvider;
    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbackProvider;
    private final ObjectProvider<TrainingAgentService> agentServiceProvider;

    public TrainingHealthService(
            @Qualifier("dataSource") ObjectProvider<DataSource> mysqlDataSourceProvider,
            @Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> pgVectorJdbcTemplateProvider,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbackProvider,
            ObjectProvider<TrainingAgentService> agentServiceProvider) {
        this.mysqlDataSourceProvider = mysqlDataSourceProvider;
        this.pgVectorJdbcTemplateProvider = pgVectorJdbcTemplateProvider;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.agentServiceProvider = agentServiceProvider;
    }

    public TrainingHealthResponse check() {
        LocalDateTime checkedAt = LocalDateTime.now();
        Map<String, TrainingHealthComponentResponse> components = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();

        TrainingHealthComponentResponse mysql = checkMySql();
        TrainingHealthComponentResponse pgvector = checkPgVector();
        TrainingHealthComponentResponse mcp = checkMcpClient();
        TrainingHealthComponentResponse agent = checkAgent();
        components.put("mysql", mysql);
        components.put("pgvector", pgvector);
        components.put("mcpClient", mcp);
        components.put("agent", agent);

        if (!"UP".equals(mysql.getStatus())) {
            issues.add("MySQL 主数据源不可用: " + mysql.getMessage());
        }
        if ("DOWN".equals(pgvector.getStatus())) {
            issues.add("pgvector 知识库不可用: " + pgvector.getMessage());
        }
        if ("DOWN".equals(mcp.getStatus())) {
            issues.add("MCP Client 不可用: " + mcp.getMessage());
        }

        String status = !"UP".equals(mysql.getStatus()) ? "DOWN"
                : issues.isEmpty() ? "UP" : "DEGRADED";
        return TrainingHealthResponse.builder()
                .status(status)
                .checkedAt(checkedAt)
                .components(components)
                .issues(issues)
                .build();
    }

    private TrainingHealthComponentResponse checkMySql() {
        DataSource dataSource;
        try {
            dataSource = mysqlDataSourceProvider.getIfAvailable();
        } catch (Exception exception) {
            return down("数据源 Bean 创建失败: " + message(exception));
        }
        if (dataSource == null) {
            return down("数据源未配置");
        }
        long start = System.nanoTime();
        try {
            Integer result = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(result)) {
                return down("SELECT 1 返回异常");
            }
            return up("MySQL 连接正常", elapsedMillis(start));
        } catch (Exception exception) {
            return down("连接检查失败: " + message(exception), elapsedMillis(start));
        }
    }

    private TrainingHealthComponentResponse checkPgVector() {
        JdbcTemplate jdbcTemplate;
        try {
            jdbcTemplate = pgVectorJdbcTemplateProvider.getIfAvailable();
        } catch (Exception exception) {
            return down("pgvector 数据源创建失败: " + message(exception));
        }
        if (jdbcTemplate == null) {
            return TrainingHealthComponentResponse.builder()
                    .status("DISABLED").message("知识库未启用").latencyMs(0L).build();
        }
        long start = System.nanoTime();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(result)) {
                return down("SELECT 1 返回异常");
            }
            return up("pgvector 连接正常", elapsedMillis(start));
        } catch (Exception exception) {
            return down("连接检查失败: " + message(exception), elapsedMillis(start));
        }
    }

    private TrainingHealthComponentResponse checkMcpClient() {
        SyncMcpToolCallbackProvider provider;
        try {
            provider = mcpToolCallbackProvider.getIfAvailable();
        } catch (Exception exception) {
            return down("MCP Client 创建失败: " + message(exception));
        }
        if (provider == null) {
            return TrainingHealthComponentResponse.builder()
                    .status("DISABLED").message("MCP Client 未启用").latencyMs(0L).toolCount(0).build();
        }
        try {
            int toolCount = provider.getToolCallbacks().length;
            if (toolCount == 0) {
                return down("MCP Client 未发现工具", 0L);
            }
            return TrainingHealthComponentResponse.builder()
                    .status("UP").message("MCP 工具已连接")
                    .latencyMs(0L).toolCount(toolCount).build();
        } catch (Exception exception) {
            return down("MCP 工具读取失败: " + message(exception));
        }
    }

    private TrainingHealthComponentResponse checkAgent() {
        try {
            return agentServiceProvider.getIfAvailable() == null
                    ? TrainingHealthComponentResponse.builder()
                    .status("DISABLED").message("Agent 未启用").latencyMs(0L).build()
                    : TrainingHealthComponentResponse.builder()
                    .status("ENABLED").message("Agent Bean 已加载").latencyMs(0L).build();
        } catch (Exception exception) {
            return down("Agent Bean 创建失败: " + message(exception));
        }
    }

    private TrainingHealthComponentResponse up(String message, long latencyMs) {
        return TrainingHealthComponentResponse.builder()
                .status("UP").message(message).latencyMs(latencyMs).build();
    }

    private TrainingHealthComponentResponse down(String message) {
        return down(message, 0L);
    }

    private TrainingHealthComponentResponse down(String message, long latencyMs) {
        return TrainingHealthComponentResponse.builder()
                .status("DOWN").message(message).latencyMs(latencyMs).build();
    }

    private long elapsedMillis(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private String message(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
