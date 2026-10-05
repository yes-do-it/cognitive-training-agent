package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingUserStatusRequest {
    @NotBlank
    private String status;
}
