package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingKnowledgeIndexTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrainingKnowledgeIndexTaskDao {
    int insert(TrainingKnowledgeIndexTaskPO task);

    TrainingKnowledgeIndexTaskPO findByTaskId(@Param("taskId") String taskId);

    List<TrainingKnowledgeIndexTaskPO> findDispatchable(@Param("limit") int limit);

    int claim(@Param("taskId") String taskId);

    int markSucceeded(@Param("taskId") String taskId,
                      @Param("chunkCounts") String chunkCounts,
                      @Param("indexedCount") int indexedCount);

    int markFailed(@Param("taskId") String taskId,
                   @Param("errorMessage") String errorMessage,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);

    int requeueRunning(@Param("errorMessage") String errorMessage);
}
