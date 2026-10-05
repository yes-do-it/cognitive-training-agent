package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TrainingTaskAdjustmentAuditResponse {
    private Long id;
    private Long taskId;
    private Long userId;
    private LocalDate trainingDate;
    private Long oldContentId;
    private Long newContentId;
    private String operatorId;
    private String reason;
    private LocalDateTime createdAt;
}