package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingRuntimeStatusCountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrainingRuntimeMonitorDao {
    List<TrainingRuntimeStatusCountPO> countTrainingTaskStatuses();

    List<Long> findStaleTrainingTaskIds(@Param("cutoff") LocalDateTime cutoff,
                                        @Param("limit") int limit);

    List<TrainingRuntimeStatusCountPO> countExecutionStatuses();

    List<Long> findStaleExecutionIds(@Param("cutoff") LocalDateTime cutoff,
                                     @Param("limit") int limit);

    List<TrainingRuntimeStatusCountPO> countIndexTaskStatuses();

    List<String> findStaleIndexTaskIds(@Param("cutoff") LocalDateTime cutoff,
                                       @Param("limit") int limit);
}
