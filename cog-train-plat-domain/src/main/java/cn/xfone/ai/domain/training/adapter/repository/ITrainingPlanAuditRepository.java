package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingPlanAuditEntity;

import java.util.List;

public interface ITrainingPlanAuditRepository {
    void save(TrainingPlanAuditEntity audit);

    List<TrainingPlanAuditEntity> findByPlanId(Long planId);
}
