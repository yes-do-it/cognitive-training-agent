package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingExecutionFinishRequest {
    @NotNull
    private Boolean success;
    private Integer score;
    private Integer durationSeconds;
    private String resultData;
    private String errorMessage;
}
