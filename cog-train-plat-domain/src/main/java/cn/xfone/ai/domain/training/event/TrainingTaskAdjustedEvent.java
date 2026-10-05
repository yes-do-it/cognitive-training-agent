package cn.xfone.ai.domain.training.event;

import java.time.LocalDate;

public record TrainingTaskAdjustedEvent(
        Long taskId,
        Long userId,
        LocalDate trainingDate,
        Long oldContentId,
        Long newContentId,
        String operatorId,
        String reason) {
}