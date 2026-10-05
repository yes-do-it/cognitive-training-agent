package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingPlanAuditPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TrainingPlanAuditDao {
    int insert(TrainingPlanAuditPO audit);

    List<TrainingPlanAuditPO> findByPlanId(@Param("planId") Long planId);
}
