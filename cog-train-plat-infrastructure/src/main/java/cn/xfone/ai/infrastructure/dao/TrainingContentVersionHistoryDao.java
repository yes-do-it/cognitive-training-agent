package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingContentVersionHistoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TrainingContentVersionHistoryDao {
    int insert(TrainingContentVersionHistoryPO history);

    List<TrainingContentVersionHistoryPO> findByContentId(@Param("contentId") Long contentId);
}
