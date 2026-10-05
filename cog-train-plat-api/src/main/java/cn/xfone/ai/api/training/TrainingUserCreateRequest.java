package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingUserCreateRequest {
    @NotBlank
    private String externalUserId;
    @NotBlank
    private String nickname;
    private String timezone;
}
