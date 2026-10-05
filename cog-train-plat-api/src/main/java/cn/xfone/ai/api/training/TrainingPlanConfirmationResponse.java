package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrainingPlanConfirmationResponse {
    private String status;
    private TrainingPlanResponse plan;
    private List<Long> contentIds;
}
