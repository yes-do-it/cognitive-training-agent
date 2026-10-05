package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.infrastructure.dao.TrainingKnowledgeIndexTaskDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingKnowledgeIndexTaskPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TrainingKnowledgeIndexTaskRepository {
    private final TrainingKnowledgeIndexTaskDao taskDao;

    public TrainingKnowledgeIndexTaskRepository(TrainingKnowledgeIndexTaskDao taskDao) {
        this.taskDao = taskDao;
    }

    public void save(TrainingKnowledgeIndexTaskPO task) {
        taskDao.insert(task);
    }

    public TrainingKnowledgeIndexTaskPO findByTaskId(String taskId) {
        return taskDao.findByTaskId(taskId);
    }

    public List<TrainingKnowledgeIndexTaskPO> findDispatchable(int limit) {
        return taskDao.findDispatchable(limit);
    }

    public boolean claim(String taskId) {
        return taskDao.claim(taskId) == 1;
    }

    public void markSucceeded(String taskId, String chunkCounts, int indexedCount) {
        taskDao.markSucceeded(taskId, chunkCounts, indexedCount);
    }

    public void markFailed(String taskId, String errorMessage, LocalDateTime nextRetryAt) {
        taskDao.markFailed(taskId, errorMessage, nextRetryAt);
    }

    public int requeueRunning(String errorMessage) {
        return taskDao.requeueRunning(errorMessage);
    }
}
