package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingHealthComponentResponse {
    private String status;
    private String message;
    private Long latencyMs;
    private Integer toolCount;
}
