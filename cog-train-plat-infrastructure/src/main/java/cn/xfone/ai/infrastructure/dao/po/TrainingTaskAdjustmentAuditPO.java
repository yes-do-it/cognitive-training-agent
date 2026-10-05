package cn.xfone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTaskAdjustmentAuditPO {
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