package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ITrainingPlanRepository {
    void save(TrainingPlanEntity plan);

    TrainingPlanEntity findById(Long planId);

    List<TrainingPlanEntity> findByUserId(Long userId);

    List<TrainingPlanEntity> findEnabledPlans();

    int updateSchedule(Long planId, LocalTime scheduleTime, Boolean enabled);
    int updatePlan(Long planId, String name, LocalDate startDate, LocalDate endDate,
                   LocalTime scheduleTime, Boolean enabled);
}


