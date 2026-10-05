package cn.xfone.ai.infrastructure.dao;

import cn.xfone.ai.infrastructure.dao.po.TrainingContentImportRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrainingContentImportRecordDao {
    int insert(TrainingContentImportRecordPO record);

    TrainingContentImportRecordPO findById(@Param("recordId") Long recordId);

    int markSucceeded(@Param("recordId") Long recordId,
                      @Param("contentId") Long contentId,
                      @Param("indexTaskId") String indexTaskId);

    int markFailed(@Param("recordId") Long recordId,
                   @Param("errorMessage") String errorMessage);
}
