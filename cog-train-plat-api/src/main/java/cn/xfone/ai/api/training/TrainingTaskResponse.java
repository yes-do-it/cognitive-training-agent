package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class TrainingTaskResponse {
    private Long id;
    private Long userId;
    private Long planId;
    private Long contentId;
    private LocalDate trainingDate;
    private String status;
}
