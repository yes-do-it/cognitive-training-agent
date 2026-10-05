package cn.xfone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingKnowledgeIndexTaskPO {
    private String taskId;
    private String contentIds;
    private String status;
    private Integer requestedCount;
    private Integer indexedCount;
    private String chunkCounts;
    private String errorMessage;
    private Integer attemptNo;
    private Integer maxAttempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
