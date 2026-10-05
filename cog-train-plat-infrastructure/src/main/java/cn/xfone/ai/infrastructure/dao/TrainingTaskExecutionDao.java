package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingTaskExecutionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrainingTaskExecutionDao {
    int insert(TrainingTaskExecutionPO execution);
    TrainingTaskExecutionPO findById(@Param("executionId") Long executionId);
    TrainingTaskExecutionPO findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    Integer findMaxAttemptNo(@Param("taskId") Long taskId);
    List<TrainingTaskExecutionPO> findByUserAndFinishedBetween(@Param("userId") Long userId,
                                                                @Param("from") LocalDateTime from,
                                                                @Param("to") LocalDateTime to);
    int updateResultIfRunning(@Param("execution") TrainingTaskExecutionPO execution,
                              @Param("expectedStatus") String expectedStatus);
}
