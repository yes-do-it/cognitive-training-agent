package cn.xfone.ai.trigger.job;

import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingTaskSchedulerTest {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    @Mock private TaskScheduler taskScheduler;
    @Mock private TrainingPlanDomainService domainService;
    @Mock private ScheduledFuture<?> scheduledFuture;

    private TrainingTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrainingTaskScheduler(taskScheduler, domainService);
        doReturn(scheduledFuture).when(taskScheduler)
                .schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void reconcileSchedulesRegistersEnabledPlan() {
        TrainingPlanEntity plan = plan(7L, LocalTime.of(9, 0));
        when(domainService.findEnabledPlans()).thenReturn(List.of(plan));

        scheduler.reconcileSchedules();

        org.mockito.ArgumentCaptor<Trigger> triggerCaptor =
                org.mockito.ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(triggerCaptor.getValue() instanceof CronTrigger);
        org.junit.jupiter.api.Assertions.assertTrue(
                triggerCaptor.getValue().toString().contains("0 0 9 * * *"));
    }

    @Test
    void reconcileSchedulesReschedulesWhenPlanSignatureChanges() {
        TrainingPlanEntity first = plan(7L, LocalTime.of(9, 0));
        TrainingPlanEntity changed = plan(7L, LocalTime.of(10, 30));
        when(domainService.findEnabledPlans()).thenReturn(List.of(first), List.of(changed));

        scheduler.reconcileSchedules();
        scheduler.reconcileSchedules();

        verify(taskScheduler, times(2))
                .schedule(any(Runnable.class), any(Trigger.class));
        verify(scheduledFuture).cancel(false);
    }

    @Test
    void reconcileSchedulesCancelsRemovedPlan() {
        TrainingPlanEntity plan = plan(7L, LocalTime.of(9, 0));
        when(domainService.findEnabledPlans()).thenReturn(List.of(plan), List.of());

        scheduler.reconcileSchedules();
        scheduler.reconcileSchedules();

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void refreshPlanRunsCatchUpGenerationWhenTodayScheduleHasPassed() {
        LocalDate today = LocalDate.now(ZONE_ID);
        TrainingPlanEntity plan = TrainingPlanEntity.builder()
                .id(7L).userId(1L).name("计划")
                .startDate(today).endDate(today.plusDays(7))
                .scheduleTime(LocalTime.MIDNIGHT).enabled(true).build();
        when(domainService.requirePlan(7L)).thenReturn(plan);

        scheduler.refreshPlan(7L);

        verify(domainService).generateDailyTask(7L, today);
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    private TrainingPlanEntity plan(Long id, LocalTime scheduleTime) {
        LocalDate today = LocalDate.now(ZONE_ID);
        return TrainingPlanEntity.builder().id(id).userId(1L).name("计划")
                .startDate(today.minusDays(1)).endDate(today.plusDays(7))
                .scheduleTime(scheduleTime).enabled(true).build();
    }
}