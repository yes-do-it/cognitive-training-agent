package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TrainingPlanAuditResponse {
    private Long id;
    private Long planId;
    private Long userId;
    private String action;
    private String reason;
    private String snapshotData;
    private LocalDateTime createdAt;
}
