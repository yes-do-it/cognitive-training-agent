package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ITrainingTaskRepository {
    int insertIgnore(TrainingTaskEntity task);
    TrainingTaskEntity findById(Long taskId);
    TrainingTaskEntity findByBusinessKey(Long userId, Long planId, LocalDate trainingDate);
    List<TrainingTaskEntity> findByUserAndDate(Long userId, LocalDate trainingDate);
    int updateStatusIfExpected(Long taskId, TrainingTaskStatusVO expectedStatus,
                               TrainingTaskStatusVO newStatus, LocalDateTime completedAt);
    int updateContentIfExpectedStatus(Long taskId, TrainingTaskStatusVO expectedStatus,
                                      Long contentId);
}