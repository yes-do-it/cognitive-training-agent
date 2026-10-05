package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.infrastructure.dao.TrainingContentVersionHistoryDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentVersionHistoryPO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TrainingContentVersionHistoryRepository {
    private final TrainingContentVersionHistoryDao historyDao;

    public TrainingContentVersionHistoryRepository(TrainingContentVersionHistoryDao historyDao) {
        this.historyDao = historyDao;
    }

    public void save(TrainingContentVersionHistoryPO history) {
        historyDao.insert(history);
    }

    public List<TrainingContentVersionHistoryPO> findByContentId(Long contentId) {
        return historyDao.findByContentId(contentId);
    }
}
