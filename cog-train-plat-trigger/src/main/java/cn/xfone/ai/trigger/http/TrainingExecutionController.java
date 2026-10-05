package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingExecutionFinishRequest;
import cn.xfone.ai.api.training.TrainingExecutionResponse;
import cn.xfone.ai.api.training.TrainingExecutionStartRequest;
import cn.xfone.ai.api.training.TrainingExecutionSummaryResponse;
import cn.xfone.ai.domain.training.model.entity.TrainingTaskExecutionEntity;
import cn.xfone.ai.trigger.application.TrainingExecutionApplicationService;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/training/executions")
public class TrainingExecutionController {
    private final TrainingExecutionApplicationService applicationService;

    public TrainingExecutionController(TrainingExecutionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/tasks/{taskId}/start")
    public Response<TrainingExecutionResponse> start(
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody TrainingExecutionStartRequest request) {
        return success(toResponse(applicationService.start(
                taskId, request.getUserId(), request.getIdempotencyKey())));
    }

    @PostMapping("/{executionId}/finish")
    public Response<TrainingExecutionResponse> finish(
            @PathVariable("executionId") Long executionId,
            @Valid @RequestBody TrainingExecutionFinishRequest request) {
        return success(toResponse(applicationService.finish(
                executionId, request.getSuccess(), request.getScore(),
                request.getDurationSeconds(), request.getResultData(), request.getErrorMessage())));
    }

    @GetMapping
    public Response<List<TrainingExecutionResponse>> list(
            @RequestParam("userId") Long userId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return success(applicationService.findFinished(userId, fromDate, toDate).stream()
                .map(this::toResponse).toList());
    }

    @GetMapping("/{executionId}")
    public Response<TrainingExecutionResponse> get(@PathVariable("executionId") Long executionId) {
        return success(toResponse(applicationService.find(executionId)));
    }

    @GetMapping("/summary")
    public Response<TrainingExecutionSummaryResponse> summary(
            @RequestParam("userId") Long userId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<TrainingTaskExecutionEntity> executions = applicationService.findFinished(
                userId, fromDate, toDate);
        return success(toSummary(userId, fromDate, toDate, executions));
    }

    private TrainingExecutionResponse toResponse(TrainingTaskExecutionEntity execution) {
        return TrainingExecutionResponse.builder().id(execution.getId()).taskId(execution.getTaskId())
                .userId(execution.getUserId()).contentId(execution.getContentId())
                .attemptNo(execution.getAttemptNo()).idempotencyKey(execution.getIdempotencyKey())
                .startedAt(execution.getStartedAt()).finishedAt(execution.getFinishedAt())
                .status(execution.getStatus().name()).score(execution.getScore())
                .durationSeconds(execution.getDurationSeconds()).resultData(execution.getResultData())
                .errorMessage(execution.getErrorMessage()).build();
    }

    private TrainingExecutionSummaryResponse toSummary(Long userId, LocalDate fromDate,
                                                       LocalDate toDate,
                                                       List<TrainingTaskExecutionEntity> executions) {
        int totalExecutions = executions.size();
        int successCount = (int) executions.stream()
                .filter(execution -> execution.getStatus().name().equals("SUCCESS"))
                .count();
        int failedCount = (int) executions.stream()
                .filter(execution -> execution.getStatus().name().equals("FAILED"))
                .count();
        int totalDurationSeconds = executions.stream()
                .map(TrainingTaskExecutionEntity::getDurationSeconds)
                .filter(value -> value != null && value >= 0)
                .mapToInt(Integer::intValue)
                .sum();
        BigDecimal durationTotal = executions.stream()
                .map(TrainingTaskExecutionEntity::getDurationSeconds)
                .filter(value -> value != null && value >= 0)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long durationCount = executions.stream()
                .map(TrainingTaskExecutionEntity::getDurationSeconds)
                .filter(value -> value != null && value >= 0)
                .count();
        BigDecimal averageDuration = durationCount == 0 ? BigDecimal.ZERO
                : durationTotal.divide(BigDecimal.valueOf(durationCount), 2, RoundingMode.HALF_UP);

        BigDecimal scoreTotal = executions.stream()
                .map(TrainingTaskExecutionEntity::getScore)
                .filter(value -> value != null)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long scoreCount = executions.stream()
                .map(TrainingTaskExecutionEntity::getScore)
                .filter(value -> value != null)
                .count();
        BigDecimal averageScore = scoreCount == 0 ? BigDecimal.ZERO
                : scoreTotal.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP);
        BigDecimal successRate = totalExecutions == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(totalExecutions), 4, RoundingMode.HALF_UP);

        return TrainingExecutionSummaryResponse.builder()
                .userId(userId).fromDate(fromDate).toDate(toDate)
                .totalExecutions(totalExecutions).successCount(successCount).failedCount(failedCount)
                .totalDurationSeconds(totalDurationSeconds).averageDurationSeconds(averageDuration)
                .averageScore(averageScore).successRate(successRate).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}


