package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrainingPlanRevokeRequest {
    @NotNull
    private Long userId;

    @NotBlank
    @Size(max = 512)
    private String reason;
}
