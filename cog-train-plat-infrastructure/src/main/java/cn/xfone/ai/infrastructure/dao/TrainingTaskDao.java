package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrainingTaskDao {
    int insertIgnore(TrainingTaskPO task);
    TrainingTaskPO findById(@Param("taskId") Long taskId);
    TrainingTaskPO findByBusinessKey(@Param("userId") Long userId,
                                     @Param("planId") Long planId,
                                     @Param("trainingDate") LocalDate trainingDate);
    List<TrainingTaskPO> findByUserAndDate(@Param("userId") Long userId,
                                           @Param("trainingDate") LocalDate trainingDate);
    int updateStatusIfExpected(@Param("taskId") Long taskId,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("newStatus") String newStatus,
                               @Param("completedAt") LocalDateTime completedAt);
    int updateContentIfExpectedStatus(@Param("taskId") Long taskId,
                                      @Param("expectedStatus") String expectedStatus,
                                      @Param("contentId") Long contentId);
}