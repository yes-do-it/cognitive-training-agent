package cn.xfone.ai.trigger.application;

import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.service.TrainingExecutionDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TrainingExecutionApplicationService {
    private final TrainingExecutionDomainService domainService;

    public TrainingExecutionApplicationService(TrainingExecutionDomainService domainService) {
        this.domainService = domainService;
    }

    @Transactional
    public TrainingTaskExecutionEntity start(Long taskId, Long userId, String idempotencyKey) {
        return domainService.startExecution(taskId, userId, idempotencyKey);
    }

    @Transactional
    public TrainingTaskExecutionEntity finish(Long executionId, boolean success,
                                              Integer score, Integer durationSeconds,
                                              String resultData, String errorMessage) {
        return domainService.finishExecution(
                executionId, success, score, durationSeconds, resultData, errorMessage);
    }

    @Transactional(readOnly = true)
    public TrainingTaskExecutionEntity find(Long executionId) {
        return domainService.findExecution(executionId);
    }

    @Transactional(readOnly = true)
    public List<TrainingTaskExecutionEntity> findFinished(Long userId,
                                                           LocalDate fromDate,
                                                           LocalDate toDate) {
        return domainService.findFinishedExecutions(userId, fromDate, toDate);
    }
}
