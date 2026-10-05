package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingPlanAuditResponse;
import cn.xfone.ai.api.training.TrainingPlanConfirmationResponse;
import cn.xfone.ai.api.training.TrainingPlanConfirmRequest;
import cn.xfone.ai.api.training.TrainingPlanContentRequest;
import cn.xfone.ai.api.training.TrainingPlanResponse;
import cn.xfone.ai.api.training.TrainingPlanRevokeRequest;
import cn.xfone.ai.api.training.TrainingPlanUpdateRequest;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanAuditEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.trigger.application.TrainingPlanConfirmationApplicationService;
import cn.xfone.ai.trigger.application.TrainingPlanContentSelection;
import cn.xfone.ai.trigger.job.TrainingTaskScheduler;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 建议方案的人工确认、撤销和审计查询入口。
 */
@RestController
@RequestMapping("/api/training/plans")
public class TrainingPlanConfirmationController {
    private final TrainingPlanConfirmationApplicationService applicationService;
    private final TrainingTaskScheduler taskScheduler;

    public TrainingPlanConfirmationController(
            TrainingPlanConfirmationApplicationService applicationService,
            TrainingTaskScheduler taskScheduler) {
        this.applicationService = applicationService;
        this.taskScheduler = taskScheduler;
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingPlanConfirmationResponse> confirm(
            @Valid @RequestBody TrainingPlanConfirmRequest request) {
        TrainingPlanEntity plan = applicationService.confirm(
                TrainingPlanEntity.builder()
                        .userId(request.getUserId())
                        .name(request.getName())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .scheduleTime(request.getScheduleTime())
                        .enabled(request.getEnabled() == null || request.getEnabled())
                        .build(),
                request.getContents().stream()
                        .map(this::toSelection)
                        .toList());

        taskScheduler.refreshPlan(plan.getId());

        List<Long> contentIds = request.getContents().stream()
                .map(TrainingPlanContentRequest::getContentId)
                .toList();
        return success(TrainingPlanConfirmationResponse.builder()
                .status("CONFIRMED")
                .plan(toResponse(plan))
                .contentIds(contentIds)
                .build());
    }

    @org.springframework.web.bind.annotation.PutMapping("/{planId}")
    public Response<TrainingPlanResponse> update(
            @PathVariable("planId") Long planId,
            @Valid @RequestBody TrainingPlanUpdateRequest request) {
        TrainingPlanEntity plan = applicationService.update(
                planId, request.getUserId(),
                TrainingPlanEntity.builder()
                        .userId(request.getUserId()).name(request.getName())
                        .startDate(request.getStartDate()).endDate(request.getEndDate())
                        .scheduleTime(request.getScheduleTime())
                        .enabled(request.getEnabled() == null || request.getEnabled())
                        .build(),
                request.getContents().stream().map(this::toSelection).toList(),
                request.getReason());
        taskScheduler.refreshPlan(planId);
        return success(toResponse(plan));
    }

    @PostMapping("/{planId}/revoke")
    public Response<TrainingPlanResponse> revoke(
            @PathVariable("planId") Long planId,
            @Valid @RequestBody TrainingPlanRevokeRequest request) {
        TrainingPlanEntity plan = applicationService.revoke(
                planId, request.getUserId(), request.getReason());
        taskScheduler.refreshPlan(planId);
        return success(toResponse(plan));
    }

    @GetMapping("/{planId}/audits")
    public Response<List<TrainingPlanAuditResponse>> audits(
            @PathVariable("planId") Long planId,
            @RequestParam("userId") Long userId) {
        List<TrainingPlanAuditResponse> audits = applicationService.findAudits(planId, userId)
                .stream().map(this::toAuditResponse).toList();
        return success(audits);
    }

    private TrainingPlanContentSelection toSelection(TrainingPlanContentRequest request) {
        return new TrainingPlanContentSelection(request.getContentId(), request.getSortOrder());
    }

    private TrainingPlanResponse toResponse(TrainingPlanEntity entity) {
        return TrainingPlanResponse.builder().id(entity.getId()).userId(entity.getUserId())
                .name(entity.getName()).startDate(entity.getStartDate()).endDate(entity.getEndDate())
                .scheduleTime(entity.getScheduleTime()).enabled(entity.getEnabled()).build();
    }

    private TrainingPlanAuditResponse toAuditResponse(TrainingPlanAuditEntity entity) {
        return TrainingPlanAuditResponse.builder().id(entity.getId()).planId(entity.getPlanId())
                .userId(entity.getUserId()).action(entity.getAction().name())
                .reason(entity.getReason()).snapshotData(entity.getSnapshotData())
                .createdAt(entity.getCreatedAt()).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}
