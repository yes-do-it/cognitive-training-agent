package cn.xfone.ai.api.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingPlanUpdateRequest {
    @NotNull private Long userId;
    @NotBlank private String name;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @NotNull private LocalTime scheduleTime;
    private Boolean enabled = true;
    @NotBlank @Size(max = 512) private String reason;
    @NotEmpty @Size(max = 20) @Valid
    private List<TrainingPlanContentRequest> contents = new ArrayList<>();
}