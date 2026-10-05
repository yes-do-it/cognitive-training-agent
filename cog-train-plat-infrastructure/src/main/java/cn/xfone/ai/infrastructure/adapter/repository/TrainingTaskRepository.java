package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;
import cn.xfone.ai.infrastructure.dao.TrainingTaskDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingTaskPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TrainingTaskRepository implements ITrainingTaskRepository {
    private final TrainingTaskDao trainingTaskDao;

    public TrainingTaskRepository(TrainingTaskDao trainingTaskDao) {
        this.trainingTaskDao = trainingTaskDao;
    }

    @Override
    public int insertIgnore(TrainingTaskEntity task) {
        return trainingTaskDao.insertIgnore(toPO(task));
    }

    @Override
    public TrainingTaskEntity findById(Long taskId) {
        return toEntity(trainingTaskDao.findById(taskId));
    }

    @Override
    public TrainingTaskEntity findByBusinessKey(Long userId, Long planId, LocalDate trainingDate) {
        return toEntity(trainingTaskDao.findByBusinessKey(userId, planId, trainingDate));
    }

    @Override
    public List<TrainingTaskEntity> findByUserAndDate(Long userId, LocalDate trainingDate) {
        return trainingTaskDao.findByUserAndDate(userId, trainingDate).stream()
                .map(this::toEntity).toList();
    }

    @Override
    public int updateStatusIfExpected(Long taskId, TrainingTaskStatusVO expectedStatus,
                                      TrainingTaskStatusVO newStatus, LocalDateTime completedAt) {
        return trainingTaskDao.updateStatusIfExpected(
                taskId, expectedStatus.name(), newStatus.name(), completedAt);
    }

    @Override
    public int updateContentIfExpectedStatus(Long taskId, TrainingTaskStatusVO expectedStatus, Long contentId) {
        return trainingTaskDao.updateContentIfExpectedStatus(taskId, expectedStatus.name(), contentId);
    }

    private TrainingTaskPO toPO(TrainingTaskEntity entity) {
        return TrainingTaskPO.builder()
                .id(entity.getId()).userId(entity.getUserId()).planId(entity.getPlanId())
                .contentId(entity.getContentId()).trainingDate(entity.getTrainingDate())
                .status(entity.getStatus().name()).scheduledAt(entity.getScheduledAt())
                .completedAt(entity.getCompletedAt()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).build();
    }

    private TrainingTaskEntity toEntity(TrainingTaskPO po) {
        if (po == null) return null;
        return TrainingTaskEntity.builder()
                .id(po.getId()).userId(po.getUserId()).planId(po.getPlanId())
                .contentId(po.getContentId()).trainingDate(po.getTrainingDate())
                .status(TrainingTaskStatusVO.valueOf(po.getStatus()))
                .scheduledAt(po.getScheduledAt()).completedAt(po.getCompletedAt())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt()).build();
    }
}
