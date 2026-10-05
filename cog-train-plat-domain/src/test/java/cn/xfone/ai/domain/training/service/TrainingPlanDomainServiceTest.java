package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingContentRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingUserRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingPlanDomainServiceTest {
    @Mock private ITrainingPlanRepository planRepository;
    @Mock private ITrainingTaskRepository taskRepository;
    @Mock private ITrainingUserRepository userRepository;
    @Mock private ITrainingContentRepository contentRepository;

    private TrainingPlanDomainService service;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanDomainService(
                planRepository, taskRepository, userRepository, contentRepository);
    }

    @Test
    void generateDailyTaskReturnsExistingTaskWithoutDuplicateInsert() {
        TrainingTaskEntity existing = task(501L, 101L);
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(planRepository.findById(7L)).thenReturn(activePlan());
        when(userRepository.findById(1L)).thenReturn(activeUser());
        when(taskRepository.findByBusinessKey(1L, 7L, date)).thenReturn(existing);

        assertSame(existing, service.generateDailyTask(7L, date));
        verify(taskRepository, never()).insertIgnore(any());
        verify(contentRepository, never()).findEnabledByPlanId(anyLong());
    }

    @Test
    void generateDailyTaskUsesDatabaseGuardForConcurrentCreation() {
        TrainingTaskEntity created = task(502L, 101L);
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(planRepository.findById(7L)).thenReturn(activePlan());
        when(userRepository.findById(1L)).thenReturn(activeUser());
        when(taskRepository.findByBusinessKey(1L, 7L, date)).thenReturn(null, created);
        when(contentRepository.findEnabledByPlanId(7L))
                .thenReturn(List.of(content(101L), content(102L)));
        when(taskRepository.insertIgnore(any(TrainingTaskEntity.class))).thenReturn(1);

        assertSame(created, service.generateDailyTask(7L, date));
        verify(taskRepository).insertIgnore(argThat(task ->
                task.getUserId().equals(1L)
                        && task.getPlanId().equals(7L)
                        && task.getContentId().equals(101L)
                        && task.getStatus() == TrainingTaskStatusVO.PENDING));
        verify(taskRepository, times(2)).findByBusinessKey(1L, 7L, date);
    }

    @Test
    void generateDailyTaskDoesNotCreateOutsidePlanDateRange() {
        when(planRepository.findById(7L)).thenReturn(activePlan());
        when(userRepository.findById(1L)).thenReturn(activeUser());

        assertNull(service.generateDailyTask(7L, LocalDate.of(2026, 9, 30)));
        verify(taskRepository, never()).findByBusinessKey(anyLong(), anyLong(), any());
        verify(contentRepository, never()).findEnabledByPlanId(anyLong());
    }

    @Test
    void generateDailyTaskRejectsInactiveUser() {
        when(planRepository.findById(7L)).thenReturn(activePlan());
        when(userRepository.findById(1L)).thenReturn(TrainingUserEntity.builder()
                .id(1L).status(TrainingUserStatusVO.INACTIVE).build());

        assertThrows(IllegalStateException.class, () ->
                service.generateDailyTask(7L, LocalDate.of(2026, 10, 5)));
        verifyNoInteractions(taskRepository, contentRepository);
    }

    @Test
    void adjustTaskContentRejectsTaskAlreadyInProgress() {
        when(taskRepository.findById(501L)).thenReturn(taskWithStatus(
                501L, TrainingTaskStatusVO.IN_PROGRESS));

        assertThrows(IllegalStateException.class, () ->
                service.adjustTaskContent(501L, 102L));
        verifyNoInteractions(contentRepository);
        verify(taskRepository, never()).updateContentIfExpectedStatus(anyLong(), any(), anyLong());
    }

    @Test
    void updateTaskStatusUsesExpectedStateTransition() {
        when(taskRepository.updateStatusIfExpected(
                eq(501L), eq(TrainingTaskStatusVO.PENDING),
                eq(TrainingTaskStatusVO.COMPLETED), any())).thenReturn(1);

        assertTrue(service.updateTaskStatus(
                501L, TrainingTaskStatusVO.PENDING, TrainingTaskStatusVO.COMPLETED));
        verify(taskRepository).updateStatusIfExpected(
                eq(501L), eq(TrainingTaskStatusVO.PENDING),
                eq(TrainingTaskStatusVO.COMPLETED), any());
    }

    private TrainingPlanEntity activePlan() {
        return TrainingPlanEntity.builder().id(7L).userId(1L)
                .name("认知训练计划")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .scheduleTime(LocalTime.of(9, 0)).enabled(true).build();
    }

    private TrainingUserEntity activeUser() {
        return TrainingUserEntity.builder().id(1L)
                .status(TrainingUserStatusVO.ACTIVE).build();
    }

    private TrainingContentEntity content(Long id) {
        return TrainingContentEntity.builder().id(id)
                .title("工作记忆训练")
                .status(TrainingContentStatusVO.ENABLED).build();
    }

    private TrainingTaskEntity task(Long id, Long contentId) {
        return TrainingTaskEntity.builder().id(id).userId(1L).planId(7L)
                .contentId(contentId).trainingDate(LocalDate.of(2026, 10, 5))
                .status(TrainingTaskStatusVO.PENDING).build();
    }

    private TrainingTaskEntity taskWithStatus(Long id, TrainingTaskStatusVO status) {
        return TrainingTaskEntity.builder().id(id).userId(1L).planId(7L)
                .contentId(101L).trainingDate(LocalDate.of(2026, 10, 5))
                .status(status).build();
    }
}