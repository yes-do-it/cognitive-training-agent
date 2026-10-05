package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TrainingPlanCreateRequest {
    @NotNull
    private Long userId;
    @NotBlank
    private String name;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    @NotNull
    private LocalTime scheduleTime;
    private Boolean enabled = true;
}
