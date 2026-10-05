package cn.xfone.ai.agent;

import cn.xfone.ai.api.training.TrainingKnowledgeIndexTaskResponse;
import cn.xfone.ai.domain.training.event.TrainingContentDeletedEvent;
import cn.xfone.ai.domain.training.event.TrainingContentUpdatedEvent;
import cn.xfone.ai.infrastructure.adapter.repository.TrainingKnowledgeIndexTaskRepository;
import cn.xfone.ai.infrastructure.dao.po.TrainingKnowledgeIndexTaskPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class TrainingKnowledgeIndexTaskService {
    private static final Logger log = LoggerFactory.getLogger(TrainingKnowledgeIndexTaskService.class);

    private final TrainingKnowledgeService knowledgeService;
    private final TrainingKnowledgeIndexTaskRepository taskRepository;
    private final ThreadPoolExecutor executor;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final int retryDelaySeconds;
    private final int retryMaxDelaySeconds;
    private final int dispatchBatchSize;
    private final boolean dispatchEnabled;

    public TrainingKnowledgeIndexTaskService(
            TrainingKnowledgeService knowledgeService,
            TrainingKnowledgeIndexTaskRepository taskRepository,
            @Qualifier("threadPoolExecutor") ThreadPoolExecutor executor,
            ObjectMapper objectMapper,
            @Value("${cognitive.knowledge.index-task.dispatch-enabled:true}") boolean dispatchEnabled,
            @Value("${cognitive.knowledge.index-task.max-attempts:3}") int maxAttempts,
            @Value("${cognitive.knowledge.index-task.retry-delay-seconds:5}") int retryDelaySeconds,
            @Value("${cognitive.knowledge.index-task.dispatch-batch-size:10}") int dispatchBatchSize) {
        this.knowledgeService = knowledgeService;
        this.taskRepository = taskRepository;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.dispatchEnabled = dispatchEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
        this.retryMaxDelaySeconds = Math.max(this.retryDelaySeconds, 300);
        this.dispatchBatchSize = Math.max(1, dispatchBatchSize);
    }

    public TrainingKnowledgeIndexTaskResponse submit(List<Long> contentIds) {
        if (!dispatchEnabled) {
            throw new IllegalStateException("当前实例未启用知识库索引任务调度");
        }
        List<Long> normalizedIds = normalizeContentIds(contentIds);
        String taskId = UUID.randomUUID().toString();
        taskRepository.save(TrainingKnowledgeIndexTaskPO.builder()
                .taskId(taskId)
                .contentIds(serializeContentIds(normalizedIds))
                .status("QUEUED")
                .requestedCount(normalizedIds.size())
                .indexedCount(0)
                .attemptNo(0)
                .maxAttempts(maxAttempts)
                .build());
        dispatch(taskId);
        return toResponse(requireTask(taskId));
    }

    public TrainingKnowledgeIndexTaskResponse find(String taskId) {
        return toResponse(requireTask(taskId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        if (!dispatchEnabled) {
            return;
        }
        int recovered = taskRepository.requeueRunning("应用重启后恢复，任务重新排队");
        if (recovered > 0) {
            log.info("requeued {} knowledge index tasks after application startup", recovered);
        }
        dispatchPending();
    }

    @Scheduled(
            fixedDelayString = "${cognitive.knowledge.index-task.poll-delay-ms:3000}",
            initialDelayString = "${cognitive.knowledge.index-task.poll-delay-ms:3000}")
    public void dispatchPending() {
        if (!dispatchEnabled) {
            return;
        }
        for (TrainingKnowledgeIndexTaskPO task : taskRepository.findDispatchable(dispatchBatchSize)) {
            dispatch(task.getTaskId());
        }
    }

    @EventListener
    public void onContentUpdated(TrainingContentUpdatedEvent event) {
        if (!dispatchEnabled || !knowledgeService.enabled()) {
            return;
        }
        try {
            submit(List.of(event.contentId()));
            log.info("submitted knowledge reindex task for contentId={}, version={}",
                    event.contentId(), event.version());
        } catch (RuntimeException exception) {
            log.error("failed to submit knowledge reindex task for contentId={}",
                    event.contentId(), exception);
        }
    }

    @EventListener
    public void onContentDeleted(TrainingContentDeletedEvent event) {
        if (!knowledgeService.enabled()) {
            return;
        }
        try {
            knowledgeService.deleteContent(event.contentId());
            log.info("deleted knowledge vectors for contentId={}, version={}",
                    event.contentId(), event.version());
        } catch (RuntimeException exception) {
            log.error("failed to delete knowledge vectors for contentId={}",
                    event.contentId(), exception);
        }
    }
    private void dispatch(String taskId) {
        try {
            executor.execute(() -> run(taskId));
        } catch (RejectedExecutionException exception) {
            log.warn("knowledge index executor is busy, task remains queued: {}", taskId);
        }
    }

    private void run(String taskId) {
        if (!taskRepository.claim(taskId)) {
            return;
        }
        TrainingKnowledgeIndexTaskPO task = requireTask(taskId);
        try {
            Map<Long, Integer> chunkCounts =
                    knowledgeService.indexContents(parseContentIds(task.getContentIds()));
            taskRepository.markSucceeded(taskId, serializeChunkCounts(chunkCounts), chunkCounts.size());
        } catch (Exception exception) {
            String message = exception.getMessage();
            taskRepository.markFailed(
                    taskId,
                    message == null || message.isBlank() ? "索引任务执行失败" : message,
                    LocalDateTime.now().plusSeconds(computeRetryDelaySeconds(task.getAttemptNo())));
        }
    }

    private long computeRetryDelaySeconds(Integer attemptNo) {
        int exponent = Math.max(0, Math.min(6, attemptNo - 1));
        long delay = (long) retryDelaySeconds * (1L << exponent);
        return Math.min(retryMaxDelaySeconds, delay);
    }
    private TrainingKnowledgeIndexTaskPO requireTask(String taskId) {
        TrainingKnowledgeIndexTaskPO task = taskRepository.findByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("索引任务不存在: " + taskId);
        }
        return task;
    }

    private TrainingKnowledgeIndexTaskResponse toResponse(TrainingKnowledgeIndexTaskPO task) {
        return TrainingKnowledgeIndexTaskResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .requestedCount(task.getRequestedCount())
                .indexedCount(task.getIndexedCount())
                .chunkCounts(parseChunkCounts(task.getChunkCounts()))
                .error(task.getErrorMessage())
                .attemptNo(task.getAttemptNo())
                .maxAttempts(task.getMaxAttempts())
                .build();
    }

    private List<Long> normalizeContentIds(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            throw new IllegalArgumentException("训练内容 ID 列表不能为空");
        }
        List<Long> normalizedIds = contentIds.stream().distinct().toList();
        normalizedIds.forEach(contentId -> {
            if (contentId == null || contentId <= 0) {
                throw new IllegalArgumentException("训练内容 ID 必须为正数");
            }
        });
        return normalizedIds;
    }

    private List<Long> parseContentIds(String contentIds) {
        if (contentIds == null || contentIds.isBlank()) {
            return List.of();
        }
        return List.of(contentIds.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    private String serializeContentIds(List<Long> contentIds) {
        return String.join(",", contentIds.stream().map(String::valueOf).toList());
    }

    private String serializeChunkCounts(Map<Long, Integer> chunkCounts) {
        try {
            return objectMapper.writeValueAsString(chunkCounts);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("索引任务结果序列化失败", exception);
        }
    }

    private Map<Long, Integer> parseChunkCounts(String chunkCounts) {
        if (chunkCounts == null || chunkCounts.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(chunkCounts, new TypeReference<LinkedHashMap<Long, Integer>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("failed to parse persisted chunk counts: {}", chunkCounts, exception);
            return new LinkedHashMap<>();
        }
    }
}




