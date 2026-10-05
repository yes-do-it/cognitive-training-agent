package cn.xfone.ai.domain.training.model.entity;

import cn.xfone.ai.domain.training.model.valobj.TrainingPlanAuditActionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanAuditEntity {
    private Long id;
    private Long planId;
    private Long userId;
    private TrainingPlanAuditActionVO action;
    private String reason;
    private String snapshotData;
    private LocalDateTime createdAt;
}
