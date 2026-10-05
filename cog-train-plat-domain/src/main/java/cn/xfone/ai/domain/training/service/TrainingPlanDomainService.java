package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingContentRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingUserRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TrainingPlanDomainService {
    private final ITrainingPlanRepository planRepository;
    private final ITrainingTaskRepository taskRepository;
    private final ITrainingUserRepository userRepository;
    private final ITrainingContentRepository contentRepository;

    public TrainingPlanDomainService(ITrainingPlanRepository planRepository,
                                     ITrainingTaskRepository taskRepository,
                                     ITrainingUserRepository userRepository,
                                     ITrainingContentRepository contentRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.contentRepository = contentRepository;
    }

    public TrainingPlanEntity createPlan(TrainingPlanEntity plan) {
        validatePlan(plan);
        requireActiveUser(plan.getUserId());
        planRepository.save(plan);
        return requirePlan(plan.getId());
    }

    public TrainingPlanEntity updateSchedule(Long planId,
                                             java.time.LocalTime scheduleTime,
                                             Boolean enabled) {
        TrainingPlanEntity plan = requirePlan(planId);
        if (Boolean.TRUE.equals(enabled)) {
            requireActiveUser(plan.getUserId());
        }
        planRepository.updateSchedule(planId, scheduleTime, enabled);
        return requirePlan(planId);
    }

    public TrainingPlanEntity updatePlan(Long planId, String name, LocalDate startDate,
                                         LocalDate endDate, java.time.LocalTime scheduleTime,
                                         Boolean enabled) {
        TrainingPlanEntity current = requirePlan(planId);
        TrainingPlanEntity candidate = TrainingPlanEntity.builder()
                .id(current.getId()).userId(current.getUserId()).name(name)
                .startDate(startDate).endDate(endDate).scheduleTime(scheduleTime)
                .enabled(enabled).build();
        validatePlan(candidate);
        if (Boolean.TRUE.equals(enabled)) {
            requireActiveUser(current.getUserId());
        }
        if (planRepository.updatePlan(planId, name, startDate, endDate,
                scheduleTime, enabled) != 1) {
            throw new IllegalStateException("训练计划更新失败，请刷新后重试");
        }
        return requirePlan(planId);
    }

    public List<Long> findPlanContentIds(Long planId) {
        requirePlan(planId);
        return contentRepository.findContentIdsByPlanId(planId);
    }

    public void bindContent(Long planId, Long contentId, Integer sortOrder) {
        requirePlan(planId);
        TrainingContentEntity content = contentRepository.findById(contentId);
        if (content == null) {
            throw new IllegalArgumentException("训练内容不存在: " + contentId);
        }
        if (content.getStatus() != TrainingContentStatusVO.ENABLED) {
            throw new IllegalStateException("训练内容已停用，不能绑定到新计划: " + contentId);
        }
        contentRepository.bindToPlan(planId, contentId, sortOrder == null ? 0 : sortOrder);
    }

    public void unbindContent(Long planId, Long contentId) {
        requirePlan(planId);
        contentRepository.unbindFromPlan(planId, contentId);
    }

    public TrainingPlanEntity requirePlan(Long planId) {
        TrainingPlanEntity plan = planRepository.findById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("训练计划不存在: " + planId);
        }
        return plan;
    }

    public List<TrainingPlanEntity> findPlans(Long userId) {
        return planRepository.findByUserId(userId);
    }

    public List<TrainingPlanEntity> findEnabledPlans() {
        return planRepository.findEnabledPlans();
    }

    public TrainingTaskEntity generateDailyTask(Long planId, LocalDate trainingDate) {
        TrainingPlanEntity plan = requirePlan(planId);
        TrainingUserEntity user = requireActiveUser(plan.getUserId());
        if (!plan.isActiveAt(trainingDate)) {
            return null;
        }

        TrainingTaskEntity existing = taskRepository.findByBusinessKey(
                plan.getUserId(), planId, trainingDate);
        if (existing != null) {
            return existing;
        }

        List<TrainingContentEntity> contents = contentRepository.findEnabledByPlanId(planId);
        Long contentId = selectContent(contents, plan, trainingDate);
        if (contentId == null) {
            throw new IllegalStateException("训练计划未绑定启用的训练内容: " + planId);
        }
        TrainingTaskEntity task = TrainingTaskEntity.builder()
                .userId(user.getId())
                .planId(planId)
                .contentId(contentId)
                .trainingDate(trainingDate)
                .status(TrainingTaskStatusVO.PENDING)
                .scheduledAt(LocalDateTime.now())
                .build();
        taskRepository.insertIgnore(task);
        return taskRepository.findByBusinessKey(plan.getUserId(), planId, trainingDate);
    }

    public TrainingTaskEntity requireTask(Long taskId) {
        TrainingTaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("训练任务不存在: " + taskId);
        }
        return task;
    }

    public TrainingTaskEntity adjustTaskContent(Long taskId, Long newContentId) {
        if (newContentId == null) {
            throw new IllegalArgumentException("新的训练内容不能为空");
        }
        TrainingTaskEntity task = requireTask(taskId);
        if (task.getStatus() != TrainingTaskStatusVO.PENDING) {
            throw new IllegalStateException("只有待执行任务可以调整当天训练内容");
        }
        TrainingContentEntity content = contentRepository.findById(newContentId);
        if (content == null) {
            throw new IllegalArgumentException("训练内容不存在: " + newContentId);
        }
        if (content.getStatus() != TrainingContentStatusVO.ENABLED) {
            throw new IllegalStateException("停用的训练内容不能用于当天任务: " + newContentId);
        }
        if (newContentId.equals(task.getContentId())) {
            throw new IllegalArgumentException("当天任务已经使用该训练内容");
        }
        if (taskRepository.updateContentIfExpectedStatus(
                taskId, TrainingTaskStatusVO.PENDING, newContentId) != 1) {
            throw new IllegalStateException("任务状态已变化，请刷新后重试");
        }
        return requireTask(taskId);
    }
    public List<TrainingTaskEntity> findTasks(Long userId, LocalDate trainingDate) {
        return taskRepository.findByUserAndDate(userId, trainingDate);
    }

    public boolean updateTaskStatus(Long taskId, TrainingTaskStatusVO expectedStatus,
                                    TrainingTaskStatusVO newStatus) {
        LocalDateTime completedAt = newStatus == TrainingTaskStatusVO.COMPLETED
                ? LocalDateTime.now() : null;
        return taskRepository.updateStatusIfExpected(taskId, expectedStatus, newStatus, completedAt) == 1;
    }

    private Long selectContent(List<TrainingContentEntity> contents,
                               TrainingPlanEntity plan, LocalDate date) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        long offset = Math.max(0, plan.getStartDate().toEpochDay() <= date.toEpochDay()
                ? date.toEpochDay() - plan.getStartDate().toEpochDay() : 0);
        return contents.get((int) (offset % contents.size())).getId();
    }

    private TrainingUserEntity requireActiveUser(Long userId) {
        TrainingUserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("训练用户不存在: " + userId);
        }
        if (user.getStatus() != TrainingUserStatusVO.ACTIVE) {
            throw new IllegalStateException("训练用户已停用: " + userId);
        }
        return user;
    }

    private void validatePlan(TrainingPlanEntity plan) {
        if (plan.getUserId() == null || plan.getName() == null || plan.getName().isBlank()) {
            throw new IllegalArgumentException("用户和计划名称不能为空");
        }
        if (plan.getStartDate() == null || plan.getEndDate() == null
                || plan.getEndDate().isBefore(plan.getStartDate())) {
            throw new IllegalArgumentException("训练计划日期范围不合法");
        }
        if (plan.getScheduleTime() == null) {
            throw new IllegalArgumentException("训练计划执行时间不能为空");
        }
    }
}




