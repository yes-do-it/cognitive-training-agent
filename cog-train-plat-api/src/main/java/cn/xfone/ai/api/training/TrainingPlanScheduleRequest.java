package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class TrainingPlanScheduleRequest {
    @NotNull
    private LocalTime scheduleTime;
    @NotNull
    private Boolean enabled;
}
