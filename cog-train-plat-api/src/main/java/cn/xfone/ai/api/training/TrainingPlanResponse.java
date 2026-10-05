package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class TrainingPlanResponse {
    private Long id;
    private Long userId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime scheduleTime;
    private Boolean enabled;
}
