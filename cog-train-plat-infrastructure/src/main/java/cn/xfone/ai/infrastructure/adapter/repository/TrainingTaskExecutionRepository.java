package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskExecutionRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskExecutionStatusVO;
import cn.xfone.ai.infrastructure.dao.TrainingTaskExecutionDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingTaskExecutionPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TrainingTaskExecutionRepository implements ITrainingTaskExecutionRepository {
    private final TrainingTaskExecutionDao executionDao;

    public TrainingTaskExecutionRepository(TrainingTaskExecutionDao executionDao) {
        this.executionDao = executionDao;
    }

    @Override
    public void save(TrainingTaskExecutionEntity execution) {
        TrainingTaskExecutionPO po = toPO(execution);
        executionDao.insert(po);
        execution.setId(po.getId());
    }

    @Override
    public TrainingTaskExecutionEntity findById(Long executionId) {
        return toEntity(executionDao.findById(executionId));
    }

    @Override
    public TrainingTaskExecutionEntity findByIdempotencyKey(String idempotencyKey) {
        return toEntity(executionDao.findByIdempotencyKey(idempotencyKey));
    }

    @Override
    public Integer findMaxAttemptNo(Long taskId) {
        Integer value = executionDao.findMaxAttemptNo(taskId);
        return value == null ? 0 : value;
    }

    @Override
    public List<TrainingTaskExecutionEntity> findByUserAndFinishedBetween(Long userId,
                                                                           LocalDateTime from,
                                                                           LocalDateTime to) {
        return executionDao.findByUserAndFinishedBetween(userId, from, to).stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public int updateResultIfRunning(TrainingTaskExecutionEntity execution,
                                     TrainingTaskExecutionStatusVO expectedStatus) {
        return executionDao.updateResultIfRunning(toPO(execution), expectedStatus.name());
    }

    private TrainingTaskExecutionPO toPO(TrainingTaskExecutionEntity execution) {
        return TrainingTaskExecutionPO.builder()
                .id(execution.getId()).taskId(execution.getTaskId()).userId(execution.getUserId())
                .contentId(execution.getContentId()).attemptNo(execution.getAttemptNo())
                .idempotencyKey(execution.getIdempotencyKey()).status(execution.getStatus().name())
                .startedAt(execution.getStartedAt()).finishedAt(execution.getFinishedAt())
                .durationSeconds(execution.getDurationSeconds()).score(execution.getScore())
                .resultData(execution.getResultData()).errorMessage(execution.getErrorMessage())
                .createdAt(execution.getCreatedAt()).updatedAt(execution.getUpdatedAt())
                .build();
    }

    private TrainingTaskExecutionEntity toEntity(TrainingTaskExecutionPO po) {
        if (po == null) return null;
        return TrainingTaskExecutionEntity.builder()
                .id(po.getId()).taskId(po.getTaskId()).userId(po.getUserId()).contentId(po.getContentId())
                .attemptNo(po.getAttemptNo()).idempotencyKey(po.getIdempotencyKey())
                .status(TrainingTaskExecutionStatusVO.valueOf(po.getStatus()))
                .startedAt(po.getStartedAt()).finishedAt(po.getFinishedAt())
                .durationSeconds(po.getDurationSeconds()).score(po.getScore())
                .resultData(po.getResultData()).errorMessage(po.getErrorMessage())
                .createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt())
                .build();
    }
}
