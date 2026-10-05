package cn.xfone.ai.trigger.job;

import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class TrainingTaskScheduler {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final long GENERATION_RETRY_DELAY_SECONDS = 30L;

    private final TaskScheduler taskScheduler;
    private final TrainingPlanDomainService domainService;
    private final Map<Long, ScheduledFuture<?>> scheduledPlans = new ConcurrentHashMap<>();
    private final Map<Long, String> scheduleSignatures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> generationRetries = new ConcurrentHashMap<>();

    public TrainingTaskScheduler(TaskScheduler taskScheduler,
                                 TrainingPlanDomainService domainService) {
        this.taskScheduler = taskScheduler;
        this.domainService = domainService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreSchedules() {
        reconcileSchedules();
        LocalDate today = LocalDate.now(ZONE_ID);
        LocalTime now = LocalTime.now(ZONE_ID);
        domainService.findEnabledPlans().stream()
                .filter(plan -> plan.isActiveAt(today)
                        && plan.getScheduleTime() != null
                        && !now.isBefore(plan.getScheduleTime()))
                .forEach(plan -> generateTaskSafely(plan.getId(), today, 1));
        log.info("恢复认知训练计划定时任务完成，数量={}", scheduledPlans.size());
    }

    public synchronized void refreshPlan(Long planId) {
        cancel(planId);
        TrainingPlanEntity plan = domainService.requirePlan(planId);
        LocalDate today = LocalDate.now(ZONE_ID);
        if (Boolean.TRUE.equals(plan.getEnabled())
                && plan.getEndDate() != null
                && !today.isAfter(plan.getEndDate())) {
            schedule(plan);
            if (plan.isActiveAt(today)
                    && plan.getScheduleTime() != null
                    && !LocalTime.now(ZONE_ID).isBefore(plan.getScheduleTime())) {
                generateTaskSafely(planId, today, 1);
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${cognitive.training.scheduler.reconcile-delay-ms:60000}",
            initialDelayString = "${cognitive.training.scheduler.reconcile-delay-ms:60000}")
    public synchronized void reconcileSchedules() {
        List<TrainingPlanEntity> activePlans = domainService.findEnabledPlans();
        Map<Long, TrainingPlanEntity> activeById = activePlans.stream()
                .collect(java.util.stream.Collectors.toMap(TrainingPlanEntity::getId, plan -> plan));
        activePlans.forEach(plan -> {
            String signature = signature(plan);
            ScheduledFuture<?> future = scheduledPlans.get(plan.getId());
            if (future == null || future.isCancelled()
                    || !signature.equals(scheduleSignatures.get(plan.getId()))) {
                schedule(plan);
            }
        });
        scheduledPlans.keySet().stream()
                .filter(planId -> !activeById.containsKey(planId))
                .toList()
                .forEach(this::cancel);
    }

    private synchronized void schedule(TrainingPlanEntity plan) {
        cancel(plan.getId());
        LocalDate today = LocalDate.now(ZONE_ID);
        if (!Boolean.TRUE.equals(plan.getEnabled()) || plan.getEndDate() == null
                || plan.getScheduleTime() == null || today.isAfter(plan.getEndDate())) {
            return;
        }
        String cron = String.format("0 %d %d * * *",
                plan.getScheduleTime().getMinute(), plan.getScheduleTime().getHour());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> generateTaskSafely(plan.getId()),
                new CronTrigger(cron, ZONE_ID));
        if (future != null) {
            scheduledPlans.put(plan.getId(), future);
            scheduleSignatures.put(plan.getId(), signature(plan));
            log.info("注册认知训练计划定时任务，planId={}, cron={}", plan.getId(), cron);
        }
    }

    private void generateTaskSafely(Long planId) {
        generateTaskSafely(planId, LocalDate.now(ZONE_ID), 1);
    }

    private void generateTaskSafely(Long planId, LocalDate trainingDate, int attempt) {
        String retryKey = retryKey(planId, trainingDate);
        try {
            LocalDate today = LocalDate.now(ZONE_ID);
            if (!today.equals(trainingDate)) {
                generationRetries.remove(retryKey);
                return;
            }
            TrainingPlanEntity plan = domainService.requirePlan(planId);
            if (!plan.isActiveAt(trainingDate)) {
                generationRetries.remove(retryKey);
                if (today.isAfter(plan.getEndDate())) {
                    cancel(planId);
                }
                return;
            }
            domainService.generateDailyTask(planId, trainingDate);
            generationRetries.remove(retryKey);
            log.info("每日训练任务生成完成，planId={}, trainingDate={}, attempt={}",
                    planId, trainingDate, attempt);
        } catch (Exception ex) {
            if (attempt >= MAX_GENERATION_ATTEMPTS) {
                generationRetries.remove(retryKey);
                log.error("生成每日训练任务失败，已达到最大重试次数，planId={}, trainingDate={}",
                        planId, trainingDate, ex);
                return;
            }
            scheduleGenerationRetry(planId, trainingDate, attempt, retryKey, ex);
        }
    }

    private void scheduleGenerationRetry(Long planId, LocalDate trainingDate,
                                         int attempt, String retryKey, Exception cause) {
        if (generationRetries.containsKey(retryKey)) {
            return;
        }
        ScheduledFuture<?> retry = taskScheduler.schedule(
                () -> {
                    generationRetries.remove(retryKey);
                    generateTaskSafely(planId, trainingDate, attempt + 1);
                },
                Instant.now().plusSeconds(GENERATION_RETRY_DELAY_SECONDS));
        if (retry != null) {
            generationRetries.put(retryKey, retry);
            log.warn("生成每日训练任务失败，将在 {} 秒后重试，planId={}, attempt={}, error={}",
                    GENERATION_RETRY_DELAY_SECONDS, planId, attempt, cause.getMessage());
        }
    }

    private String signature(TrainingPlanEntity plan) {
        return plan.getStartDate() + "|" + plan.getEndDate() + "|"
                + plan.getScheduleTime() + "|" + plan.getEnabled();
    }

    private String retryKey(Long planId, LocalDate trainingDate) {
        return planId + ":" + trainingDate;
    }

    private synchronized void cancel(Long planId) {
        ScheduledFuture<?> future = scheduledPlans.remove(planId);
        scheduleSignatures.remove(planId);
        if (future != null) {
            future.cancel(false);
            log.info("取消认知训练计划定时任务，planId={}", planId);
        }
        generationRetries.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(planId + ":")) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduledPlans.keySet().forEach(this::cancel);
        generationRetries.values().forEach(future -> future.cancel(false));
        generationRetries.clear();
    }
}