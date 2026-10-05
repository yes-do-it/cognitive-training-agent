package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrainingTaskAdjustmentRequest {
    @NotNull
    private Long contentId;

    @NotBlank
    @Size(max = 64)
    private String operatorId;

    @NotBlank
    @Size(max = 512)
    private String reason;
}