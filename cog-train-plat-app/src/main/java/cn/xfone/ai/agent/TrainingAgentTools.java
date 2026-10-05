package cn.xfone.ai.agent;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskExecutionRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TrainingAgentTools {

    private final TrainingPlanDomainService trainingPlanDomainService;
    private final TrainingContentDomainService trainingContentDomainService;
    private final ITrainingTaskExecutionRepository executionRepository;

    public TrainingAgentTools(TrainingPlanDomainService trainingPlanDomainService,
                              TrainingContentDomainService trainingContentDomainService,
                              ITrainingTaskExecutionRepository executionRepository) {
        this.trainingPlanDomainService = trainingPlanDomainService;
        this.trainingContentDomainService = trainingContentDomainService;
        this.executionRepository = executionRepository;
    }

    @Tool(description = "查询指定用户在指定日期的启用训练计划和每日训练任务，同时返回可用于编排的启用训练内容候选。只读，不会修改训练数据。")
    public Map<String, Object> queryTrainingContext(
            @ToolParam(description = "训练用户ID") Long userId,
            @ToolParam(description = "训练日期，格式为 yyyy-MM-dd") String trainingDate) {
        LocalDate date = LocalDate.parse(trainingDate);
        List<Map<String, Object>> plans = trainingPlanDomainService.findEnabledPlans().stream()
                .filter(plan -> userId.equals(plan.getUserId()))
                .filter(plan -> plan.isActiveAt(date))
                .map(this::planSnapshot)
                .toList();
        List<Map<String, Object>> tasks = trainingPlanDomainService.findTasks(userId, date).stream()
                .map(this::taskSnapshot)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("trainingDate", date.toString());
        result.put("plans", plans);
        result.put("tasks", tasks);
        result.put("contentCandidates", queryTrainingContents());
        return result;
    }

    @Tool(description = "查询当前启用的认知训练内容候选，返回内容 ID、标题、类型和难度。只读。")
    public List<Map<String, Object>> queryTrainingContents() {
        return trainingContentDomainService.findAll().stream()
                .filter(content -> content.getStatus() == TrainingContentStatusVO.ENABLED)
                .map(this::contentSnapshot)
                .toList();
    }

    @Tool(description = "查询指定用户在日期范围内的训练执行历史，并返回状态、分数和耗时。最多查询31天。只读。")
    public List<Map<String, Object>> queryTrainingHistory(
            @ToolParam(description = "训练用户ID") Long userId,
            @ToolParam(description = "开始日期，格式为 yyyy-MM-dd") String fromDate,
            @ToolParam(description = "结束日期，格式为 yyyy-MM-dd") String toDate) {
        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);
        if (to.isBefore(from) || to.isAfter(from.plusDays(31))) {
            throw new IllegalArgumentException("历史查询范围必须在1至32天内");
        }
        return executionRepository.findByUserAndFinishedBetween(
                        userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().map(this::executionSnapshot).toList();
    }

    @Tool(description = "统计指定用户日期范围内的训练完成次数、成功率和平均分。最多查询31天。只读。")
    public Map<String, Object> queryTrainingStats(
            @ToolParam(description = "训练用户ID") Long userId,
            @ToolParam(description = "开始日期，格式为 yyyy-MM-dd") String fromDate,
            @ToolParam(description = "结束日期，格式为 yyyy-MM-dd") String toDate) {
        List<Map<String, Object>> history = queryTrainingHistory(userId, fromDate, toDate);
        long successCount = history.stream().filter(item -> "SUCCESS".equals(item.get("status"))).count();
        long failedCount = history.stream().filter(item -> "FAILED".equals(item.get("status"))).count();
        double averageScore = history.stream().map(item -> item.get("score"))
                .filter(Number.class::isInstance).mapToInt(value -> ((Number) value).intValue())
                .average().orElse(0D);
        double averageDurationSeconds = history.stream().map(item -> item.get("durationSeconds"))
                .filter(Number.class::isInstance).mapToInt(value -> ((Number) value).intValue())
                .average().orElse(0D);
        Map<String, List<Map<String, Object>>> byContent = history.stream()
                .filter(item -> item.get("contentId") != null)
                .collect(Collectors.groupingBy(item -> String.valueOf(item.get("contentId")),
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> contentPerformance = byContent.entrySet().stream()
                .map(entry -> {
                    List<Map<String, Object>> items = entry.getValue();
                    long contentSuccessCount = items.stream()
                            .filter(item -> "SUCCESS".equals(item.get("status"))).count();
                    double contentAverageScore = items.stream().map(item -> item.get("score"))
                            .filter(Number.class::isInstance).mapToInt(value -> ((Number) value).intValue())
                            .average().orElse(0D);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("contentId", entry.getKey());
                    item.put("executionCount", items.size());
                    item.put("successRate", items.isEmpty() ? 0D : (double) contentSuccessCount / items.size());
                    item.put("averageScore", contentAverageScore);
                    return item;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalExecutions", history.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("successRate", history.isEmpty() ? 0D : (double) successCount / history.size());
        result.put("averageScore", averageScore);
        result.put("averageDurationSeconds", averageDurationSeconds);
        result.put("contentPerformance", contentPerformance);
        return result;
    }

    @Tool(description = "查询指定用户当前启用的训练计划摘要。只读，不会修改训练数据。")
    public List<Map<String, Object>> queryTrainingPlans(
            @ToolParam(description = "训练用户ID") Long userId) {
        return trainingPlanDomainService.findEnabledPlans().stream()
                .filter(plan -> userId.equals(plan.getUserId()))
                .map(this::planSnapshot)
                .toList();
    }

    private Map<String, Object> planSnapshot(TrainingPlanEntity plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", plan.getId());
        snapshot.put("name", plan.getName());
        snapshot.put("startDate", plan.getStartDate());
        snapshot.put("endDate", plan.getEndDate());
        snapshot.put("scheduleTime", plan.getScheduleTime());
        snapshot.put("enabled", plan.getEnabled());
        return snapshot;
    }

    private Map<String, Object> contentSnapshot(TrainingContentEntity content) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contentId", content.getId());
        snapshot.put("title", content.getTitle());
        snapshot.put("contentType", content.getContentType());
        snapshot.put("difficulty", content.getDifficulty());
        return snapshot;
    }

    private Map<String, Object> taskSnapshot(TrainingTaskEntity task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("planId", task.getPlanId());
        snapshot.put("contentId", task.getContentId());
        snapshot.put("trainingDate", task.getTrainingDate());
        snapshot.put("status", task.getStatus());
        snapshot.put("scheduledAt", task.getScheduledAt());
        snapshot.put("completedAt", task.getCompletedAt());
        return snapshot;
    }

    private Map<String, Object> executionSnapshot(TrainingTaskExecutionEntity execution) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("executionId", execution.getId());
        snapshot.put("taskId", execution.getTaskId());
        snapshot.put("contentId", execution.getContentId());
        snapshot.put("attemptNo", execution.getAttemptNo());
        snapshot.put("status", execution.getStatus().name());
        snapshot.put("startedAt", execution.getStartedAt());
        snapshot.put("finishedAt", execution.getFinishedAt());
        snapshot.put("durationSeconds", execution.getDurationSeconds());
        snapshot.put("score", execution.getScore());
        snapshot.put("errorMessage", execution.getErrorMessage());
        return snapshot;
    }
}
