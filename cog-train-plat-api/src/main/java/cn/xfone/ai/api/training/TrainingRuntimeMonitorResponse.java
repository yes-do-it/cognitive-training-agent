package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TrainingRuntimeMonitorResponse {
    private LocalDateTime generatedAt;
    private Integer staleThresholdMinutes;
    private Map<String, Integer> trainingTaskStatusCounts;
    private Map<String, Integer> executionStatusCounts;
    private Map<String, Integer> indexTaskStatusCounts;
    private List<Long> staleTrainingTaskIds;
    private List<Long> staleExecutionIds;
    private List<String> staleIndexTaskIds;
    private List<String> alerts;
}
