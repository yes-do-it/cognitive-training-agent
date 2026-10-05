package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingTaskAdjustmentAuditPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TrainingTaskAdjustmentAuditDao {
    int insert(TrainingTaskAdjustmentAuditPO audit);

    List<TrainingTaskAdjustmentAuditPO> findByTaskId(@Param("taskId") Long taskId);

    List<TrainingTaskAdjustmentAuditPO> findByUserAndDate(@Param("userId") Long userId,
                                                          @Param("trainingDate") LocalDate trainingDate);
}