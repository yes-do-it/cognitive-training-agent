package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingContentRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.infrastructure.dao.TrainingContentDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentPO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TrainingContentRepository implements ITrainingContentRepository {
    private final TrainingContentDao contentDao;

    public TrainingContentRepository(TrainingContentDao contentDao) {
        this.contentDao = contentDao;
    }

    @Override
    public void save(TrainingContentEntity content) {
        TrainingContentPO po = toPO(content);
        contentDao.insert(po);
        content.setId(po.getId());
    }

    @Override
    public TrainingContentEntity findById(Long contentId) {
        return toEntity(contentDao.findById(contentId));
    }

    @Override
    public int update(TrainingContentEntity content) {
        return contentDao.update(toPO(content));
    }

    @Override
    public List<TrainingContentEntity> findAll() {
        return contentDao.findAll().stream().map(this::toEntity).toList();
    }

    @Override
    public List<TrainingContentEntity> findEnabledByPlanId(Long planId) {
        return contentDao.findEnabledByPlanId(planId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<Long> findContentIdsByPlanId(Long planId) {
        return contentDao.findContentIdsByPlanId(planId);
    }

    @Override
    public int bindToPlan(Long planId, Long contentId, Integer sortOrder) {
        return contentDao.bindToPlan(planId, contentId, sortOrder);
    }

    @Override
    public int unbindFromPlan(Long planId, Long contentId) {
        return contentDao.unbindFromPlan(planId, contentId);
    }

    private TrainingContentPO toPO(TrainingContentEntity content) {
        return TrainingContentPO.builder()
                .id(content.getId())
                .title(content.getTitle())
                .contentType(content.getContentType())
                .difficulty(content.getDifficulty())
                .contentBody(content.getContentBody())
                .status(content.getStatus().name())
                .version(content.getVersion())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .build();
    }

    private TrainingContentEntity toEntity(TrainingContentPO po) {
        if (po == null) return null;
        return TrainingContentEntity.builder()
                .id(po.getId())
                .title(po.getTitle())
                .contentType(po.getContentType())
                .difficulty(po.getDifficulty())
                .contentBody(po.getContentBody())
                .status(TrainingContentStatusVO.valueOf(po.getStatus()))
                .version(po.getVersion())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}






