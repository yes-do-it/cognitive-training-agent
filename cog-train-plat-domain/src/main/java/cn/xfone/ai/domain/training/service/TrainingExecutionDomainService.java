package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskExecutionRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskExecutionStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class TrainingExecutionDomainService {
    private final ITrainingTaskRepository taskRepository;
    private final ITrainingTaskExecutionRepository executionRepository;

    public TrainingExecutionDomainService(ITrainingTaskRepository taskRepository,
                                          ITrainingTaskExecutionRepository executionRepository) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
    }

    public TrainingTaskExecutionEntity startExecution(Long taskId, Long userId,
                                                       String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        TrainingTaskExecutionEntity existing = executionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            verifyIdempotencyReuse(existing, taskId, userId);
            return existing;
        }

        TrainingTaskEntity task = requireTask(taskId);
        if (!task.getUserId().equals(userId)) {
            throw new IllegalArgumentException("训练任务不属于当前用户");
        }
        if (task.getStatus() != TrainingTaskStatusVO.PENDING) {
            existing = executionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                verifyIdempotencyReuse(existing, taskId, userId);
                return existing;
            }
            throw new IllegalStateException("只有待执行任务可以开始执行");
        }
        if (taskRepository.updateStatusIfExpected(
                taskId, TrainingTaskStatusVO.PENDING, TrainingTaskStatusVO.IN_PROGRESS, null) != 1) {
            existing = executionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                verifyIdempotencyReuse(existing, taskId, userId);
                return existing;
            }
            throw new IllegalStateException("训练任务状态已变化，请刷新后重试");
        }

        int attemptNo = executionRepository.findMaxAttemptNo(taskId) + 1;
        TrainingTaskExecutionEntity execution = TrainingTaskExecutionEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .contentId(task.getContentId())
                .attemptNo(attemptNo)
                .idempotencyKey(idempotencyKey)
                .status(TrainingTaskExecutionStatusVO.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
        executionRepository.save(execution);
        return executionRepository.findByIdempotencyKey(idempotencyKey);
    }

    public TrainingTaskExecutionEntity finishExecution(Long executionId, boolean success,
                                                        Integer score, Integer durationSeconds,
                                                        String resultData, String errorMessage) {
        validateResult(score, durationSeconds);
        TrainingTaskExecutionEntity execution = executionRepository.findById(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在: " + executionId);
        }
        TrainingTaskExecutionStatusVO target = success
                ? TrainingTaskExecutionStatusVO.SUCCESS
                : TrainingTaskExecutionStatusVO.FAILED;
        if (execution.getStatus() != TrainingTaskExecutionStatusVO.RUNNING) {
            if (execution.getStatus() == target
                    && Objects.equals(execution.getScore(), score)
                    && Objects.equals(execution.getDurationSeconds(), durationSeconds)
                    && Objects.equals(execution.getResultData(), resultData)
                    && Objects.equals(execution.getErrorMessage(), errorMessage)) {
                return execution;
            }
            throw new IllegalStateException("执行记录已完成或已失败");
        }

        execution.setStatus(target);
        execution.setScore(score);
        execution.setDurationSeconds(durationSeconds);
        execution.setResultData(resultData);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(LocalDateTime.now());
        if (executionRepository.updateResultIfRunning(
                execution, TrainingTaskExecutionStatusVO.RUNNING) != 1) {
            TrainingTaskExecutionEntity current = executionRepository.findById(executionId);
            if (current != null && current.getStatus() == target
                    && Objects.equals(current.getScore(), score)
                    && Objects.equals(current.getDurationSeconds(), durationSeconds)
                    && Objects.equals(current.getResultData(), resultData)
                    && Objects.equals(current.getErrorMessage(), errorMessage)) {
                return current;
            }
            throw new IllegalStateException("执行记录已完成或已失败");
        }

        TrainingTaskStatusVO expectedTaskStatus = TrainingTaskStatusVO.IN_PROGRESS;
        TrainingTaskStatusVO targetTaskStatus = success
                ? TrainingTaskStatusVO.COMPLETED : TrainingTaskStatusVO.PENDING;
        if (taskRepository.updateStatusIfExpected(
                execution.getTaskId(), expectedTaskStatus, targetTaskStatus,
                success ? LocalDateTime.now() : null) != 1) {
            throw new IllegalStateException("训练任务状态更新失败");
        }
        return executionRepository.findById(executionId);
    }

    public TrainingTaskExecutionEntity findExecution(Long executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("执行记录ID不能为空");
        }
        TrainingTaskExecutionEntity execution = executionRepository.findById(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在: " + executionId);
        }
        return execution;
    }

    public List<TrainingTaskExecutionEntity> findFinishedExecutions(Long userId,
                                                                      LocalDate fromDate,
                                                                      LocalDate toDate) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("训练用户ID必须为正数");
        }
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("查询日期范围不合法");
        }
        if (toDate.isAfter(fromDate.plusDays(31))) {
            throw new IllegalArgumentException("查询日期范围不能超过32天");
        }
        return executionRepository.findByUserAndFinishedBetween(
                userId, fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    private void verifyIdempotencyReuse(TrainingTaskExecutionEntity existing,
                                        Long taskId, Long userId) {
        if (!Objects.equals(existing.getTaskId(), taskId)
                || !Objects.equals(existing.getUserId(), userId)) {
            throw new IllegalArgumentException("幂等键已被其他训练请求使用");
        }
    }

    private void validateResult(Integer score, Integer durationSeconds) {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("训练得分必须在 0 至 100 之间");
        }
        if (durationSeconds != null && (durationSeconds < 0)) {
            throw new IllegalArgumentException("训练耗时不能小于 0 秒");
        }
    }

    private TrainingTaskEntity requireTask(Long taskId) {
        TrainingTaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("训练任务不存在: " + taskId);
        }
        return task;
    }
}