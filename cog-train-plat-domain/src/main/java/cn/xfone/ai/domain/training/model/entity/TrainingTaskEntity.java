package cn.xfone.ai.domain.training.model.entity;

import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTaskEntity {
    private Long id;
    private Long userId;
    private Long planId;
    private Long contentId;
    private LocalDate trainingDate;
    private TrainingTaskStatusVO status;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
