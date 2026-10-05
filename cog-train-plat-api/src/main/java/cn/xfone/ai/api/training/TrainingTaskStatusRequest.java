package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingTaskStatusRequest {
    @NotBlank
    private String expectedStatus;
}
