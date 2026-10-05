package cn.xfone.ai.domain.training.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanEntity {
    private Long id;
    private Long userId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime scheduleTime;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActiveAt(LocalDate date) {
        return Boolean.TRUE.equals(enabled)
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }
}
