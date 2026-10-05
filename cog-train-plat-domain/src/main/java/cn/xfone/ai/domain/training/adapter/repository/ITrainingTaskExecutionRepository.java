package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskExecutionStatusVO;

import java.time.LocalDateTime;
import java.util.List;

public interface ITrainingTaskExecutionRepository {
    void save(TrainingTaskExecutionEntity execution);

    TrainingTaskExecutionEntity findById(Long executionId);

    TrainingTaskExecutionEntity findByIdempotencyKey(String idempotencyKey);

    Integer findMaxAttemptNo(Long taskId);

    List<TrainingTaskExecutionEntity> findByUserAndFinishedBetween(Long userId,
                                                                    LocalDateTime from,
                                                                    LocalDateTime to);

    int updateResultIfRunning(TrainingTaskExecutionEntity execution,
                              TrainingTaskExecutionStatusVO expectedStatus);
}
