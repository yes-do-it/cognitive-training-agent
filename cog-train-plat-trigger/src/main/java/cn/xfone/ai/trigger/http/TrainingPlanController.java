package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingPlanContentRequest;
import cn.xfone.ai.api.training.TrainingPlanCreateRequest;
import cn.xfone.ai.api.training.TrainingPlanResponse;
import cn.xfone.ai.api.training.TrainingPlanScheduleRequest;
import cn.xfone.ai.api.training.TrainingTaskResponse;
import cn.xfone.ai.api.training.TrainingTaskStatusRequest;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanEntity;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingTaskStatusVO;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import cn.xfone.ai.trigger.job.TrainingTaskScheduler;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/training")
public class TrainingPlanController {
    private final TrainingPlanDomainService domainService;
    private final TrainingTaskScheduler taskScheduler;

    public TrainingPlanController(TrainingPlanDomainService domainService,
                                  TrainingTaskScheduler taskScheduler) {
        this.domainService = domainService;
        this.taskScheduler = taskScheduler;
    }

    @GetMapping("/plans")
    public Response<List<TrainingPlanResponse>> queryPlans(@RequestParam("userId") Long userId) {
        List<TrainingPlanResponse> plans = domainService.findPlans(userId).stream()
                .map(this::toResponse).toList();
        return success(plans);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingPlanResponse> createPlan(
            @Valid @RequestBody TrainingPlanCreateRequest request) {
        TrainingPlanEntity plan = domainService.createPlan(TrainingPlanEntity.builder()
                .userId(request.getUserId()).name(request.getName())
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .scheduleTime(request.getScheduleTime())
                .enabled(request.getEnabled() == null || request.getEnabled()).build());
        taskScheduler.refreshPlan(plan.getId());
        return success(toResponse(plan));
    }

    @PutMapping("/plans/{planId}/schedule")
    public Response<TrainingPlanResponse> updateSchedule(
            @PathVariable("planId") Long planId,
            @Valid @RequestBody TrainingPlanScheduleRequest request) {
        TrainingPlanEntity plan = domainService.updateSchedule(
                planId, request.getScheduleTime(), request.getEnabled());
        taskScheduler.refreshPlan(planId);
        return success(toResponse(plan));
    }

    @GetMapping("/plans/{planId}/contents")
    public Response<List<Long>> queryPlanContentIds(
            @PathVariable("planId") Long planId,
            @RequestParam("userId") Long userId) {
        TrainingPlanEntity plan = domainService.requirePlan(planId);
        if (!userId.equals(plan.getUserId())) {
            throw new IllegalArgumentException("无权查看该训练计划");
        }
        return success(domainService.findPlanContentIds(planId));
    }

    @PostMapping("/plans/{planId}/contents")
    public Response<Void> bindContent(@PathVariable("planId") Long planId,
                                      @Valid @RequestBody TrainingPlanContentRequest request) {
        domainService.bindContent(planId, request.getContentId(), request.getSortOrder());
        return success(null);
    }

    @DeleteMapping("/plans/{planId}/contents/{contentId}")
    public Response<Void> unbindContent(@PathVariable("planId") Long planId,
                                        @PathVariable("contentId") Long contentId) {
        domainService.unbindContent(planId, contentId);
        return success(null);
    }

    @GetMapping("/tasks")
    public Response<List<TrainingTaskResponse>> queryTasks(
            @RequestParam("userId") Long userId,
            @RequestParam("trainingDate") LocalDate trainingDate) {
        List<TrainingTaskResponse> tasks = domainService.findTasks(userId, trainingDate)
                .stream().map(this::toResponse).toList();
        return success(tasks);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Response<Void> completeTask(@PathVariable("taskId") Long taskId,
                                       @Valid @RequestBody TrainingTaskStatusRequest request) {
        return updateTaskStatus(taskId, request.getExpectedStatus(), TrainingTaskStatusVO.COMPLETED);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Response<Void> cancelTask(@PathVariable("taskId") Long taskId,
                                     @Valid @RequestBody TrainingTaskStatusRequest request) {
        return updateTaskStatus(taskId, request.getExpectedStatus(), TrainingTaskStatusVO.CANCELLED);
    }

    private Response<Void> updateTaskStatus(Long taskId, String expectedStatus,
                                            TrainingTaskStatusVO target) {
        if (!domainService.updateTaskStatus(taskId,
                TrainingTaskStatusVO.valueOf(expectedStatus), target)) {
            throw new IllegalStateException("训练任务状态已变化，请刷新后重试");
        }
        return success(null);
    }

    private TrainingPlanResponse toResponse(TrainingPlanEntity entity) {
        return TrainingPlanResponse.builder().id(entity.getId()).userId(entity.getUserId())
                .name(entity.getName()).startDate(entity.getStartDate()).endDate(entity.getEndDate())
                .scheduleTime(entity.getScheduleTime()).enabled(entity.getEnabled()).build();
    }

    private TrainingTaskResponse toResponse(TrainingTaskEntity entity) {
        return TrainingTaskResponse.builder().id(entity.getId()).userId(entity.getUserId())
                .planId(entity.getPlanId()).contentId(entity.getContentId())
                .trainingDate(entity.getTrainingDate()).status(entity.getStatus().name()).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}


