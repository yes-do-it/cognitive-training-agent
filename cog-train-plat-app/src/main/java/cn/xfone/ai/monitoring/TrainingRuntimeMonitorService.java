package cn.xfone.ai.monitoring;

import cn.xfone.ai.api.training.TrainingRuntimeMonitorResponse;
import cn.xfone.ai.infrastructure.dao.TrainingRuntimeMonitorDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingRuntimeStatusCountPO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingRuntimeMonitorService {
    private final TrainingRuntimeMonitorDao monitorDao;

    public TrainingRuntimeMonitorService(TrainingRuntimeMonitorDao monitorDao) {
        this.monitorDao = monitorDao;
    }

    public TrainingRuntimeMonitorResponse snapshot(Integer staleMinutes, Integer limit) {
        int normalizedStaleMinutes = staleMinutes == null ? 30 : staleMinutes;
        int normalizedLimit = limit == null ? 50 : limit;
        if (normalizedStaleMinutes < 1 || normalizedStaleMinutes > 1440) {
            throw new IllegalArgumentException("超时阈值必须在1至1440分钟之间");
        }
        if (normalizedLimit < 1 || normalizedLimit > 200) {
            throw new IllegalArgumentException("告警任务数量上限必须在1至200之间");
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime cutoff = generatedAt.minusMinutes(normalizedStaleMinutes);
        Map<String, Integer> trainingTaskStatuses = toStatusMap(monitorDao.countTrainingTaskStatuses());
        Map<String, Integer> executionStatuses = toStatusMap(monitorDao.countExecutionStatuses());
        Map<String, Integer> indexTaskStatuses = toStatusMap(monitorDao.countIndexTaskStatuses());
        List<Long> staleTrainingTaskIds = monitorDao.findStaleTrainingTaskIds(cutoff, normalizedLimit);
        List<Long> staleExecutionIds = monitorDao.findStaleExecutionIds(cutoff, normalizedLimit);
        List<String> staleIndexTaskIds = monitorDao.findStaleIndexTaskIds(cutoff, normalizedLimit);

        List<String> alerts = new ArrayList<>();
        addStatusAlert(alerts, "训练任务存在失败状态", trainingTaskStatuses.get("FAILED"));
        addStatusAlert(alerts, "训练执行存在失败状态", executionStatuses.get("FAILED"));
        addStatusAlert(alerts, "知识库索引存在失败状态", indexTaskStatuses.get("FAILED"));
        if (!staleTrainingTaskIds.isEmpty()) {
            alerts.add("存在超过阈值未更新的训练任务: " + staleTrainingTaskIds.size());
        }
        if (!staleExecutionIds.isEmpty()) {
            alerts.add("存在超过阈值未完成的训练执行: " + staleExecutionIds.size());
        }
        if (!staleIndexTaskIds.isEmpty()) {
            alerts.add("存在超过阈值未完成的知识库索引任务: " + staleIndexTaskIds.size());
        }

        return TrainingRuntimeMonitorResponse.builder()
                .generatedAt(generatedAt)
                .staleThresholdMinutes(normalizedStaleMinutes)
                .trainingTaskStatusCounts(trainingTaskStatuses)
                .executionStatusCounts(executionStatuses)
                .indexTaskStatusCounts(indexTaskStatuses)
                .staleTrainingTaskIds(staleTrainingTaskIds)
                .staleExecutionIds(staleExecutionIds)
                .staleIndexTaskIds(staleIndexTaskIds)
                .alerts(alerts)
                .build();
    }

    private Map<String, Integer> toStatusMap(List<TrainingRuntimeStatusCountPO> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TrainingRuntimeStatusCountPO row : rows) {
            result.put(row.getStatus(), row.getItemCount());
        }
        return result;
    }

    private void addStatusAlert(List<String> alerts, String message, Integer count) {
        if (count != null && count > 0) {
            alerts.add(message + ": " + count);
        }
    }
}
