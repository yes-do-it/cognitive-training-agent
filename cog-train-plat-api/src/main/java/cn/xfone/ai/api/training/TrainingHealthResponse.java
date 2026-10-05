package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TrainingHealthResponse {
    private String status;
    private LocalDateTime checkedAt;
    private Map<String, TrainingHealthComponentResponse> components;
    private List<String> issues;
}
