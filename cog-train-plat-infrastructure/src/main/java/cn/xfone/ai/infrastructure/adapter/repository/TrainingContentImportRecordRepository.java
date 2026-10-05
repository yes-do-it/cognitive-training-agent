package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.infrastructure.dao.TrainingContentImportRecordDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentImportRecordPO;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingContentImportRecordRepository {
    private final TrainingContentImportRecordDao recordDao;

    public TrainingContentImportRecordRepository(TrainingContentImportRecordDao recordDao) {
        this.recordDao = recordDao;
    }

    public void create(TrainingContentImportRecordPO record) {
        recordDao.insert(record);
    }

    public TrainingContentImportRecordPO findById(Long recordId) {
        return recordDao.findById(recordId);
    }

    public void markSucceeded(Long recordId, Long contentId, String indexTaskId) {
        recordDao.markSucceeded(recordId, contentId, indexTaskId);
    }

    public void markFailed(Long recordId, String errorMessage) {
        recordDao.markFailed(recordId, errorMessage);
    }
}
