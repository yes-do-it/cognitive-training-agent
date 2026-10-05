package cn.xfone.ai.trigger.application;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanAuditRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanAuditEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingPlanAuditActionVO;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将用户确认的 Agent 方案转换为领域命令，并以一个事务完成计划、内容绑定和审计记录。
 */
@Service
public class TrainingPlanConfirmationApplicationService {
    private final TrainingPlanDomainService domainService;
    private final ITrainingPlanAuditRepository auditRepository;

    public TrainingPlanConfirmationApplicationService(
            TrainingPlanDomainService domainService,
            ITrainingPlanAuditRepository auditRepository) {
        this.domainService = domainService;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public TrainingPlanEntity confirm(TrainingPlanEntity plan,
                                      List<TrainingPlanContentSelection> selections) {
        validateSelections(selections);

        TrainingPlanEntity savedPlan = domainService.createPlan(plan);
        for (TrainingPlanContentSelection selection : selections) {
            domainService.bindContent(savedPlan.getId(), selection.contentId(),
                    selection.sortOrder() == null ? 0 : selection.sortOrder());
        }
        auditRepository.save(TrainingPlanAuditEntity.builder()
                .planId(savedPlan.getId())
                .userId(savedPlan.getUserId())
                .action(TrainingPlanAuditActionVO.CONFIRMED)
                .reason("用户确认 Agent 训练编排建议")
                .snapshotData(snapshot(savedPlan, selections))
                .build());
        return savedPlan;
    }

    @Transactional
    public TrainingPlanEntity update(Long planId, Long userId, TrainingPlanEntity changes,
                                     List<TrainingPlanContentSelection> selections,
                                     String reason) {
        validateSelections(selections);
        TrainingPlanEntity current = domainService.requirePlan(planId);
        verifyOwner(current, userId);
        TrainingPlanEntity updated = domainService.updatePlan(
                planId, changes.getName(), changes.getStartDate(), changes.getEndDate(),
                changes.getScheduleTime(), changes.getEnabled());

        Set<Long> requestedIds = selections.stream()
                .map(TrainingPlanContentSelection::contentId)
                .collect(Collectors.toSet());
        for (Long oldContentId : domainService.findPlanContentIds(planId)) {
            if (!requestedIds.contains(oldContentId)) {
                domainService.unbindContent(planId, oldContentId);
            }
        }
        for (TrainingPlanContentSelection selection : selections) {
            domainService.bindContent(planId, selection.contentId(),
                    selection.sortOrder() == null ? 0 : selection.sortOrder());
        }
        auditRepository.save(TrainingPlanAuditEntity.builder()
                .planId(updated.getId()).userId(updated.getUserId())
                .action(TrainingPlanAuditActionVO.UPDATED).reason(reason)
                .snapshotData(snapshot(updated, selections)).build());
        return updated;
    }

    @Transactional
    public TrainingPlanEntity revoke(Long planId, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("撤销原因不能为空");
        }
        TrainingPlanEntity plan = domainService.requirePlan(planId);
        verifyOwner(plan, userId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new IllegalStateException("训练计划已处于停用状态: " + planId);
        }

        TrainingPlanEntity revokedPlan = domainService.updateSchedule(
                planId, plan.getScheduleTime(), false);
        auditRepository.save(TrainingPlanAuditEntity.builder()
                .planId(revokedPlan.getId())
                .userId(revokedPlan.getUserId())
                .action(TrainingPlanAuditActionVO.REVOKED)
                .reason(reason)
                .snapshotData(snapshot(revokedPlan, List.of()))
                .build());
        return revokedPlan;
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanAuditEntity> findAudits(Long planId, Long userId) {
        TrainingPlanEntity plan = domainService.requirePlan(planId);
        verifyOwner(plan, userId);
        return auditRepository.findByPlanId(planId);
    }

    private void validateSelections(List<TrainingPlanContentSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("确认方案至少需要一个训练内容");
        }
        Set<Long> contentIds = new HashSet<>();
        for (TrainingPlanContentSelection selection : selections) {
            if (selection == null || selection.contentId() == null) {
                throw new IllegalArgumentException("训练内容不能为空");
            }
            if (!contentIds.add(selection.contentId())) {
                throw new IllegalArgumentException("确认方案不能重复绑定同一训练内容: "
                        + selection.contentId());
            }
            if (selection.sortOrder() != null && selection.sortOrder() < 0) {
                throw new IllegalArgumentException("训练内容排序值不能小于 0");
            }
        }
    }

    private void verifyOwner(TrainingPlanEntity plan, Long userId) {
        if (userId == null || !userId.equals(plan.getUserId())) {
            throw new IllegalArgumentException("无权操作该训练计划");
        }
    }

    private String snapshot(TrainingPlanEntity plan,
                            List<TrainingPlanContentSelection> selections) {
        String contentIds = selections.stream()
                .map(selection -> String.valueOf(selection.contentId()))
                .collect(Collectors.joining(","));
        return "name=" + plan.getName()
                + ";startDate=" + plan.getStartDate()
                + ";endDate=" + plan.getEndDate()
                + ";scheduleTime=" + plan.getScheduleTime()
                + ";enabled=" + plan.getEnabled()
                + ";contentIds=" + contentIds;
    }
}
