package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.infrastructure.dao.TrainingPlanDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingPlanPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public class TrainingPlanRepository implements ITrainingPlanRepository {
    private final TrainingPlanDao trainingPlanDao;

    public TrainingPlanRepository(TrainingPlanDao trainingPlanDao) {
        this.trainingPlanDao = trainingPlanDao;
    }

    @Override
    public void save(TrainingPlanEntity plan) {
        TrainingPlanPO po = toPO(plan);
        trainingPlanDao.insert(po);
        plan.setId(po.getId());
    }

    @Override
    public TrainingPlanEntity findById(Long planId) {
        return toEntity(trainingPlanDao.findById(planId));
    }

    @Override
    public List<TrainingPlanEntity> findByUserId(Long userId) {
        return trainingPlanDao.findByUserId(userId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<TrainingPlanEntity> findEnabledPlans() {
        return trainingPlanDao.findEnabledPlans().stream().map(this::toEntity).toList();
    }

    @Override
    public int updateSchedule(Long planId, LocalTime scheduleTime, Boolean enabled) {
        return trainingPlanDao.updateSchedule(planId, scheduleTime, enabled);
    }

    @Override
    public int updatePlan(Long planId, String name, LocalDate startDate, LocalDate endDate,
                          LocalTime scheduleTime, Boolean enabled) {
        return trainingPlanDao.updatePlan(planId, name, startDate, endDate, scheduleTime, enabled);
    }

    private TrainingPlanPO toPO(TrainingPlanEntity entity) {
        return TrainingPlanPO.builder()
                .id(entity.getId()).userId(entity.getUserId()).name(entity.getName())
                .startDate(entity.getStartDate()).endDate(entity.getEndDate())
                .scheduleTime(entity.getScheduleTime()).enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).build();
    }

    private TrainingPlanEntity toEntity(TrainingPlanPO po) {
        if (po == null) return null;
        return TrainingPlanEntity.builder()
                .id(po.getId()).userId(po.getUserId()).name(po.getName())
                .startDate(po.getStartDate()).endDate(po.getEndDate())
                .scheduleTime(po.getScheduleTime()).enabled(po.getEnabled())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt()).build();
    }
}






