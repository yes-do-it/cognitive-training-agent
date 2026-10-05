package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TrainingExecutionSummaryResponse {
    private Long userId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer totalExecutions;
    private Integer successCount;
    private Integer failedCount;
    private Integer totalDurationSeconds;
    private BigDecimal averageDurationSeconds;
    private BigDecimal averageScore;
    private BigDecimal successRate;
}
