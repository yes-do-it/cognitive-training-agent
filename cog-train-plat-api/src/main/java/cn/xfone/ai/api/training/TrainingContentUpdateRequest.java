package cn.xfone.ai.api.training;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingContentUpdateRequest {
    @NotBlank
    private String title;
    private String contentType;
    private Integer difficulty;
    @NotBlank
    private String contentBody;
    @NotBlank
    private String status;
    /** Optional optimistic-lock version. */
    private Integer expectedVersion;
}
