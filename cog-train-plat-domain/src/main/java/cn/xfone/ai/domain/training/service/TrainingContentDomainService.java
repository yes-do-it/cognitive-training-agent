package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingContentRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;

import java.util.List;

public class TrainingContentDomainService {
    private final ITrainingContentRepository contentRepository;

    public TrainingContentDomainService(ITrainingContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public TrainingContentEntity createContent(TrainingContentEntity content) {
        if (content.getTitle() == null || content.getTitle().isBlank()) {
            throw new IllegalArgumentException("训练内容标题不能为空");
        }
        if (content.getContentBody() == null || content.getContentBody().isBlank()) {
            throw new IllegalArgumentException("训练内容正文不能为空");
        }
        if (content.getStatus() == null) {
            content.setStatus(TrainingContentStatusVO.ENABLED);
        }
        if (content.getVersion() == null) {
            content.setVersion(1);
        }
        contentRepository.save(content);
        return requireContent(content.getId());
    }

    public List<TrainingContentEntity> findAll() {
        return contentRepository.findAll();
    }

    public TrainingContentEntity requireContent(Long contentId) {
        TrainingContentEntity content = contentRepository.findById(contentId);
        if (content == null) {
            throw new IllegalArgumentException("训练内容不存在: " + contentId);
        }
        return content;
    }

    /**
     * Updates content with optimistic version checking. The caller may provide an
     * expected version; when omitted, the current version is used automatically.
     */
    public TrainingContentEntity updateContent(TrainingContentEntity content) {
        TrainingContentEntity current = requireContent(content.getId());
        if (content.getVersion() != null
                && !content.getVersion().equals(current.getVersion())) {
            throw new IllegalStateException("训练内容版本冲突，当前版本为: " + current.getVersion());
        }
        content.setVersion(current.getVersion());
        if (contentRepository.update(content) != 1) {
            throw new IllegalStateException("训练内容更新失败，可能已被其他请求修改");
        }
        return requireContent(content.getId());
    }

    /**
     * Logical deletion keeps the content row and all plan references intact.
     * A disabled item is treated as an idempotent delete.
     */
    public TrainingContentEntity disableContent(Long contentId) {
        TrainingContentEntity current = requireContent(contentId);
        if (current.getStatus() == TrainingContentStatusVO.DISABLED) {
            return current;
        }
        current.setStatus(TrainingContentStatusVO.DISABLED);
        current.setVersion(current.getVersion());
        if (contentRepository.update(current) != 1) {
            throw new IllegalStateException("训练内容删除失败，可能已被其他请求修改");
        }
        return requireContent(contentId);
    }
}


