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
public class TrainingPlanAuditPO {
    private Long id;
    private Long planId;
    private Long userId;
    private String action;
    private String reason;
    private String snapshotData;
    private LocalDateTime createdAt;
}
