package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingPlanContentRequest {
    @NotNull
    private Long contentId;
    private Integer sortOrder = 0;
}
