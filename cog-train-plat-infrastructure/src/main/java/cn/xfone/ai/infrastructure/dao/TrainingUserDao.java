package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrainingUserDao {
    int insert(TrainingUserPO user);
    TrainingUserPO findById(@Param("userId") Long userId);
    TrainingUserPO findByExternalUserId(@Param("externalUserId") String externalUserId);
    int updateStatus(@Param("userId") Long userId, @Param("status") String status);
}
