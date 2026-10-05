package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;

import java.util.List;

public interface ITrainingContentRepository {
    void save(TrainingContentEntity content);

    TrainingContentEntity findById(Long contentId);

    List<TrainingContentEntity> findAll();

    int update(TrainingContentEntity content);

    List<TrainingContentEntity> findEnabledByPlanId(Long planId);

    List<Long> findContentIdsByPlanId(Long planId);

    int bindToPlan(Long planId, Long contentId, Integer sortOrder);

    int unbindFromPlan(Long planId, Long contentId);
}


