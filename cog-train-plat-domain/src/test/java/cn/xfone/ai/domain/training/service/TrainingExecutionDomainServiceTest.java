package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskExecutionRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskExecutionStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingExecutionDomainServiceTest {
    @Mock private ITrainingTaskRepository taskRepository;
    @Mock private ITrainingTaskExecutionRepository executionRepository;

    private TrainingExecutionDomainService service;

    @BeforeEach
    void setUp() {
        service = new TrainingExecutionDomainService(taskRepository, executionRepository);
    }

    @Test
    void startExecutionReturnsExistingExecutionForRepeatedIdempotencyKey() {
        TrainingTaskExecutionEntity existing = execution(
                900L, 501L, 1L, TrainingTaskExecutionStatusVO.RUNNING);
        when(executionRepository.findByIdempotencyKey("key-1")).thenReturn(existing);

        assertSame(existing, service.startExecution(501L, 1L, "key-1"));
        verifyNoInteractions(taskRepository);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void startExecutionReturnsExistingExecutionWhenConcurrentClaimLoses() {
        TrainingTaskExecutionEntity existing = execution(
                900L, 501L, 1L, TrainingTaskExecutionStatusVO.RUNNING);
        when(executionRepository.findByIdempotencyKey("key-2")).thenReturn(null, existing);
        when(taskRepository.findById(501L)).thenReturn(pendingTask(501L));
        when(taskRepository.updateStatusIfExpected(
                eq(501L), eq(TrainingTaskStatusVO.PENDING),
                eq(TrainingTaskStatusVO.IN_PROGRESS), isNull())).thenReturn(0);

        assertSame(existing, service.startExecution(501L, 1L, "key-2"));
        verify(executionRepository, never()).findMaxAttemptNo(anyLong());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void startExecutionRejectsIdempotencyKeyReusedByAnotherTask() {
        TrainingTaskExecutionEntity existing = execution(
                900L, 502L, 1L, TrainingTaskExecutionStatusVO.RUNNING);
        when(executionRepository.findByIdempotencyKey("key-3")).thenReturn(existing);

        assertThrows(IllegalArgumentException.class, () ->
                service.startExecution(501L, 1L, "key-3"));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void finishExecutionIsIdempotentForSameResult() {
        TrainingTaskExecutionEntity existing = execution(
                901L, 501L, 1L, TrainingTaskExecutionStatusVO.SUCCESS);
        existing.setScore(90);
        existing.setDurationSeconds(60);
        existing.setResultData("完成");
        when(executionRepository.findById(901L)).thenReturn(existing);

        assertSame(existing, service.finishExecution(
                901L, true, 90, 60, "完成", null));
        verifyNoInteractions(taskRepository);
        verify(executionRepository, never()).updateResultIfRunning(any(), any());
    }

    @Test
    void finishExecutionRejectsConflictingDuplicateResult() {
        TrainingTaskExecutionEntity existing = execution(
                901L, 501L, 1L, TrainingTaskExecutionStatusVO.SUCCESS);
        existing.setScore(90);
        existing.setDurationSeconds(60);
        existing.setResultData("完成");
        when(executionRepository.findById(901L)).thenReturn(existing);

        assertThrows(IllegalStateException.class, () ->
                service.finishExecution(901L, true, 80, 60, "完成", null));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void finishExecutionUpdatesExecutionAndTaskByExpectedStates() {
        TrainingTaskExecutionEntity running = execution(
                902L, 501L, 1L, TrainingTaskExecutionStatusVO.RUNNING);
        TrainingTaskExecutionEntity finished = execution(
                902L, 501L, 1L, TrainingTaskExecutionStatusVO.SUCCESS);
        finished.setScore(95);
        finished.setDurationSeconds(45);
        finished.setResultData("完成");
        when(executionRepository.findById(902L)).thenReturn(running, finished);
        when(executionRepository.updateResultIfRunning(
                any(TrainingTaskExecutionEntity.class),
                eq(TrainingTaskExecutionStatusVO.RUNNING))).thenReturn(1);
        when(taskRepository.updateStatusIfExpected(
                eq(501L), eq(TrainingTaskStatusVO.IN_PROGRESS),
                eq(TrainingTaskStatusVO.COMPLETED), any())).thenReturn(1);

        assertSame(finished, service.finishExecution(
                902L, true, 95, 45, "完成", null));

        verify(executionRepository).updateResultIfRunning(
                argThat(execution -> execution.getStatus()
                        == TrainingTaskExecutionStatusVO.SUCCESS),
                eq(TrainingTaskExecutionStatusVO.RUNNING));
        verify(taskRepository).updateStatusIfExpected(
                eq(501L), eq(TrainingTaskStatusVO.IN_PROGRESS),
                eq(TrainingTaskStatusVO.COMPLETED), any());
    }

    @Test
    void finishExecutionRejectsInvalidScoreBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () ->
                service.finishExecution(902L, true, 101, 45, "完成", null));
        verifyNoInteractions(executionRepository, taskRepository);
    }

    @Test
    void finishedExecutionQueryRejectsDateRangeLongerThanThirtyTwoDays() {
        assertThrows(IllegalArgumentException.class, () ->
                service.findFinishedExecutions(1L,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 3)));
        verifyNoInteractions(executionRepository);
    }

    @Test
    void finishedExecutionQueryAllowsThirtyTwoDayRange() {
        when(executionRepository.findByUserAndFinishedBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        assertNotNull(service.findFinishedExecutions(1L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
        verify(executionRepository).findByUserAndFinishedBetween(
                eq(1L), any(), any());
    }

    private TrainingTaskEntity pendingTask(Long taskId) {
        return TrainingTaskEntity.builder().id(taskId).userId(1L)
                .contentId(101L).status(TrainingTaskStatusVO.PENDING).build();
    }

    private TrainingTaskExecutionEntity execution(Long id, Long taskId, Long userId,
                                                   TrainingTaskExecutionStatusVO status) {
        return TrainingTaskExecutionEntity.builder().id(id).taskId(taskId).userId(userId)
                .contentId(101L).attemptNo(1).idempotencyKey("key-" + id)
                .status(status).build();
    }
}