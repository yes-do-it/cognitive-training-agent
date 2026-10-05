package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingPlanPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface TrainingPlanDao {
    int insert(TrainingPlanPO plan);
    TrainingPlanPO findById(@Param("planId") Long planId);
    List<TrainingPlanPO> findByUserId(@Param("userId") Long userId);
    List<TrainingPlanPO> findEnabledPlans();
    int updateSchedule(@Param("planId") Long planId,
                       @Param("scheduleTime") LocalTime scheduleTime,
                       @Param("enabled") Boolean enabled);
    int updatePlan(@Param("planId") Long planId, @Param("name") String name,
                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                   @Param("scheduleTime") LocalTime scheduleTime, @Param("enabled") Boolean enabled);
}


