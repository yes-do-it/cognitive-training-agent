package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TrainingKnowledgeIndexTaskResponse {
    private String taskId;
    private String status;
    private Integer requestedCount;
    private Integer indexedCount;
    private Map<Long, Integer> chunkCounts;
    private String error;
    private Integer attemptNo;
    private Integer maxAttempts;
}
