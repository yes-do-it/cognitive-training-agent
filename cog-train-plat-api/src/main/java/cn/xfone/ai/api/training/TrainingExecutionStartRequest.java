package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingExecutionStartRequest {
    @NotNull
    private Long userId;
    @NotBlank
    private String idempotencyKey;
}
