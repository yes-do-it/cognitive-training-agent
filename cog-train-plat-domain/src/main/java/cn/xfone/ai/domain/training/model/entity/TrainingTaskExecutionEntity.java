package cn.xfone.ai.domain.training.model.entity;

import cn.xfone.ai.domain.training.model.valobj.TrainingTaskExecutionStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTaskExecutionEntity {
    private Long id;
    private Long taskId;
    private Long userId;
    private Long contentId;
    private Integer attemptNo;
    private String idempotencyKey;
    private TrainingTaskExecutionStatusVO status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer durationSeconds;
    private Integer score;
    private String resultData;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
