package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingContentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TrainingContentDao {
    int insert(TrainingContentPO content);
    TrainingContentPO findById(@Param("contentId") Long contentId);
    List<TrainingContentPO> findAll();
    int update(TrainingContentPO content);
    List<TrainingContentPO> findEnabledByPlanId(@Param("planId") Long planId);
    List<Long> findContentIdsByPlanId(@Param("planId") Long planId);
    int bindToPlan(@Param("planId") Long planId,
                   @Param("contentId") Long contentId,
                   @Param("sortOrder") Integer sortOrder);
    int unbindFromPlan(@Param("planId") Long planId,
                       @Param("contentId") Long contentId);
}


