package cn.xfone.ai.api.training;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingExecutionResponse {
    private Long id;
    private Long taskId;
    private Long userId;
    private Long contentId;
    private Integer attemptNo;
    private String idempotencyKey;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private Integer score;
    private Integer durationSeconds;
    private String resultData;
    private String errorMessage;
}
