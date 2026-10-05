package cn.xfone.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "cognitive.mcp.enabled", havingValue = "true")
public class TrainingMcpServerConfiguration {

    private final ObjectMapper objectMapper;

    public TrainingMcpServerConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public HttpServletSseServerTransportProvider trainingMcpTransport() {
        return HttpServletSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .baseUrl("/mcp")
                .sseEndpoint("/sse")
                .messageEndpoint("/message")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> trainingMcpServlet(
            HttpServletSseServerTransportProvider transport) {
        return new ServletRegistrationBean<>(transport, "/mcp/*");
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer trainingMcpServer(HttpServletSseServerTransportProvider transport,
                                           TrainingAgentTools tools) {
        return McpServer.sync(transport)
                .serverInfo("cognitive-training-mcp", "1.0.0")
                .instructions("认知训练只读查询工具。Agent 必须先查询事实，再生成待确认编排方案。")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(contextTool(tools), historyTool(tools), statsTool(tools), contentTool(tools))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification contextTool(TrainingAgentTools tools) {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "query_training_context",
                        "查询用户指定日期的训练计划和每日任务，只读。",
                        objectSchema(Map.of(
                                "userId", Map.of("type", "integer"),
                                "trainingDate", Map.of("type", "string", "description", "yyyy-MM-dd")),
                                List.of("userId", "trainingDate"))),
                (exchange, arguments) -> invoke(() -> tools.queryTrainingContext(
                        requiredLong(arguments, "userId"), requiredString(arguments, "trainingDate"))));
    }

    private McpServerFeatures.SyncToolSpecification contentTool(TrainingAgentTools tools) {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "query_training_contents",
                        "查询当前启用的训练内容候选，返回内容 ID、标题、类型和难度，只读。",
                        objectSchema(Map.of(), List.of())),
                (exchange, arguments) -> invoke(tools::queryTrainingContents));
    }

    private McpServerFeatures.SyncToolSpecification historyTool(TrainingAgentTools tools) {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "query_training_history",
                        "查询用户日期范围内的训练执行历史，只读，最多31天。",
                        objectSchema(Map.of(
                                "userId", Map.of("type", "integer"),
                                "fromDate", Map.of("type", "string", "description", "yyyy-MM-dd"),
                                "toDate", Map.of("type", "string", "description", "yyyy-MM-dd")),
                                List.of("userId", "fromDate", "toDate"))),
                (exchange, arguments) -> invoke(() -> tools.queryTrainingHistory(
                        requiredLong(arguments, "userId"), requiredString(arguments, "fromDate"),
                        requiredString(arguments, "toDate"))));
    }

    private McpServerFeatures.SyncToolSpecification statsTool(TrainingAgentTools tools) {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "query_training_stats",
                        "统计用户日期范围内的训练完成次数、成功率和平均分，只读，最多31天。",
                        objectSchema(Map.of(
                                "userId", Map.of("type", "integer"),
                                "fromDate", Map.of("type", "string", "description", "yyyy-MM-dd"),
                                "toDate", Map.of("type", "string", "description", "yyyy-MM-dd")),
                                List.of("userId", "fromDate", "toDate"))),
                (exchange, arguments) -> invoke(() -> tools.queryTrainingStats(
                        requiredLong(arguments, "userId"), requiredString(arguments, "fromDate"),
                        requiredString(arguments, "toDate"))));
    }

    private McpSchema.JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema("object", properties, required, false, Map.of(), Map.of());
    }

    private McpSchema.CallToolResult invoke(ToolCall call) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(call.call()))
                    .isError(false)
                    .build();
        } catch (Exception exception) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(exception.getMessage() == null ? "训练工具调用失败" : exception.getMessage())
                    .isError(true)
                    .build();
        }
    }

    private Long requiredLong(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(requiredString(arguments, key));
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        return value.toString();
    }

    @FunctionalInterface
    private interface ToolCall {
        Object call();
    }
}

