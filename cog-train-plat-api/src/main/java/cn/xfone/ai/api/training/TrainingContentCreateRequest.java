package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingContentCreateRequest {
    @NotBlank
    private String title;
    private String contentType;
    private Integer difficulty;
    @NotBlank
    private String contentBody;
    private String status;
}
