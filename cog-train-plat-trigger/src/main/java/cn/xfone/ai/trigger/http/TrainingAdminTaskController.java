package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingTaskAdjustmentRequest;
import cn.xfone.ai.api.training.TrainingTaskResponse;
import cn.xfone.ai.domain.training.event.TrainingTaskAdjustedEvent;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/admin/training/tasks")
public class TrainingAdminTaskController {
    private static final Long DEMO_USER_ID = 1L;
    private static final String DEMO_OPERATOR_ID = "demo-admin";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TrainingPlanDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    public TrainingAdminTaskController(TrainingPlanDomainService domainService,
                                       ApplicationEventPublisher eventPublisher) {
        this.domainService = domainService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 演示范围：仅允许固定演示管理员调整用户 1 的服务器当天待执行任务。
     * 正式环境应替换为登录态角色校验，并从服务端上下文获取操作人。
     */
    @PutMapping("/{taskId}/content")
    public Response<TrainingTaskResponse> adjustContent(
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody TrainingTaskAdjustmentRequest request) {
        TrainingTaskEntity before = domainService.requireTask(taskId);
        if (!DEMO_USER_ID.equals(before.getUserId())) {
            throw new IllegalStateException("演示管理员只能调整用户 1 的任务");
        }
        if (!LocalDate.now(BUSINESS_ZONE).equals(before.getTrainingDate())) {
            throw new IllegalStateException("演示管理员只能调整服务器当天的任务");
        }
        if (!DEMO_OPERATOR_ID.equals(request.getOperatorId())) {
            throw new IllegalArgumentException("演示操作人必须为 demo-admin");
        }

        TrainingTaskEntity after = domainService.adjustTaskContent(taskId, request.getContentId());
        eventPublisher.publishEvent(new TrainingTaskAdjustedEvent(
                after.getId(),
                after.getUserId(),
                after.getTrainingDate(),
                before.getContentId(),
                after.getContentId(),
                DEMO_OPERATOR_ID,
                request.getReason()));
        return success(toResponse(after));
    }

    private TrainingTaskResponse toResponse(TrainingTaskEntity entity) {
        return TrainingTaskResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .planId(entity.getPlanId())
                .contentId(entity.getContentId())
                .trainingDate(entity.getTrainingDate())
                .status(entity.getStatus().name())
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}