package cn.xfone.ai.monitoring;

import cn.xfone.ai.api.training.TrainingHealthComponentResponse;
import cn.xfone.ai.api.training.TrainingHealthResponse;
import cn.xfone.ai.api.training.TrainingRuntimeMonitorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
public class TrainingPrometheusMetricsController {
    private final TrainingRuntimeMonitorService monitorService;
    private final TrainingHealthService healthService;

    public TrainingPrometheusMetricsController(TrainingRuntimeMonitorService monitorService,
                                                TrainingHealthService healthService) {
        this.monitorService = monitorService;
        this.healthService = healthService;
    }

    @GetMapping(value = "/prometheus", produces = "text/plain; version=0.0.4; charset=utf-8")
    public String prometheus(
            @RequestParam(value = "staleMinutes", required = false) Integer staleMinutes,
            @RequestParam(value = "limit", required = false) Integer limit) {
        TrainingRuntimeMonitorResponse monitor = monitorService.snapshot(staleMinutes, limit);
        TrainingHealthResponse health = healthService.check();
        StringBuilder metrics = new StringBuilder(2048);

        appendHelp(metrics, "cognitive_training_tasks", "Training task count by status.");
        appendType(metrics, "cognitive_training_tasks", "gauge");
        appendStatusMetrics(metrics, "cognitive_training_tasks", monitor.getTrainingTaskStatusCounts());

        appendHelp(metrics, "cognitive_training_executions", "Training execution count by status.");
        appendType(metrics, "cognitive_training_executions", "gauge");
        appendStatusMetrics(metrics, "cognitive_training_executions", monitor.getExecutionStatusCounts());

        appendHelp(metrics, "cognitive_training_index_tasks", "Knowledge index task count by status.");
        appendType(metrics, "cognitive_training_index_tasks", "gauge");
        appendStatusMetrics(metrics, "cognitive_training_index_tasks", monitor.getIndexTaskStatusCounts());

        appendHelp(metrics, "cognitive_training_stale_tasks", "Stale task count detected by the runtime monitor.");
        appendType(metrics, "cognitive_training_stale_tasks", "gauge");
        appendGauge(metrics, "cognitive_training_stale_tasks", "type", "training_task",
                monitor.getStaleTrainingTaskIds().size());
        appendGauge(metrics, "cognitive_training_stale_tasks", "type", "execution",
                monitor.getStaleExecutionIds().size());
        appendGauge(metrics, "cognitive_training_stale_tasks", "type", "index_task",
                monitor.getStaleIndexTaskIds().size());

        appendHelp(metrics, "cognitive_training_alerts", "Number of runtime monitor alerts.");
        appendType(metrics, "cognitive_training_alerts", "gauge");
        appendGauge(metrics, "cognitive_training_alerts", null, null, monitor.getAlerts().size());

        appendHelp(metrics, "cognitive_training_component_health", "Component health state; 1 means healthy or enabled.");
        appendType(metrics, "cognitive_training_component_health", "gauge");
        for (Map.Entry<String, TrainingHealthComponentResponse> entry : health.getComponents().entrySet()) {
            TrainingHealthComponentResponse component = entry.getValue();
            appendGauge(metrics, "cognitive_training_component_health", "component",
                    entry.getKey(), isHealthy(component.getStatus()) ? 1 : 0,
                    "status", component.getStatus());
        }

        appendHelp(metrics, "cognitive_training_mcp_tools", "Number of tools discovered from MCP Client.");
        appendType(metrics, "cognitive_training_mcp_tools", "gauge");
        int toolCount = health.getComponents().get("mcpClient").getToolCount() == null
                ? 0 : health.getComponents().get("mcpClient").getToolCount();
        appendGauge(metrics, "cognitive_training_mcp_tools", null, null, toolCount);

        appendHelp(metrics, "cognitive_training_health", "Overall application health; 1 means UP.");
        appendType(metrics, "cognitive_training_health", "gauge");
        appendGauge(metrics, "cognitive_training_health", "status", health.getStatus(),
                "UP".equals(health.getStatus()) ? 1 : 0);
        return metrics.toString();
    }

    private void appendStatusMetrics(StringBuilder metrics, String metric, Map<String, Integer> statuses) {
        for (Map.Entry<String, Integer> entry : statuses.entrySet()) {
            appendGauge(metrics, metric, "status", entry.getKey(),
                    entry.getValue() == null ? 0 : entry.getValue());
        }
    }

    private void appendHelp(StringBuilder metrics, String metric, String help) {
        metrics.append("# HELP ").append(metric).append(' ').append(help).append('\n');
    }

    private void appendType(StringBuilder metrics, String metric, String type) {
        metrics.append("# TYPE ").append(metric).append(' ').append(type).append('\n');
    }

    private void appendGauge(StringBuilder metrics, String metric, String labelName,
                             String labelValue, int value, String... extraLabels) {
        metrics.append(metric);
        if (labelName != null) {
            metrics.append('{').append(labelName).append("=\"")
                    .append(escape(labelValue)).append('"');
            for (int i = 0; i + 1 < extraLabels.length; i += 2) {
                metrics.append(',').append(extraLabels[i]).append("=\"")
                        .append(escape(extraLabels[i + 1])).append('"');
            }
            metrics.append('}');
        }
        metrics.append(' ').append(value).append('\n');
    }

    private boolean isHealthy(String status) {
        return "UP".equals(status) || "ENABLED".equals(status);
    }

    private String escape(String value) {
        return value == null ? "unknown" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n");
    }
}
