package cn.xfone.ai.agent;

import cn.xfone.ai.api.training.TrainingContentImportRecordResponse;
import cn.xfone.ai.api.training.TrainingContentVersionDiffResponse;
import cn.xfone.ai.api.training.TrainingContentVersionResponse;
import cn.xfone.ai.domain.training.event.TrainingContentCreatedEvent;
import cn.xfone.ai.domain.training.event.TrainingContentDeletedEvent;
import cn.xfone.ai.domain.training.event.TrainingContentUpdatedEvent;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import cn.xfone.ai.infrastructure.adapter.repository.TrainingContentImportRecordRepository;
import cn.xfone.ai.infrastructure.adapter.repository.TrainingContentVersionHistoryRepository;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentImportRecordPO;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentVersionHistoryPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TrainingContentAuditService {
    private static final Logger log = LoggerFactory.getLogger(TrainingContentAuditService.class);

    private final TrainingContentDomainService contentDomainService;
    private final TrainingContentVersionHistoryRepository historyRepository;
    private final TrainingContentImportRecordRepository importRecordRepository;

    public TrainingContentAuditService(
            TrainingContentDomainService contentDomainService,
            TrainingContentVersionHistoryRepository historyRepository,
            TrainingContentImportRecordRepository importRecordRepository) {
        this.contentDomainService = contentDomainService;
        this.historyRepository = historyRepository;
        this.importRecordRepository = importRecordRepository;
    }

    @EventListener
    public void onContentCreated(TrainingContentCreatedEvent event) {
        saveSnapshot(event.contentId(), event.sourceType(), event.sourceFileName());
    }

    @EventListener
    public void onContentUpdated(TrainingContentUpdatedEvent event) {
        saveSnapshot(event.contentId(), "UPDATE", null);
    }

    @EventListener
    public void onContentDeleted(TrainingContentDeletedEvent event) {
        saveSnapshot(event.contentId(), "DELETE", null);
    }

    public List<TrainingContentVersionResponse> findVersions(Long contentId) {
        contentDomainService.requireContent(contentId);
        return historyRepository.findByContentId(contentId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    public TrainingContentVersionDiffResponse findDiff(
            Long contentId, Integer fromVersion, Integer toVersion) {
        contentDomainService.requireContent(contentId);
        if (fromVersion == null || toVersion == null || fromVersion <= 0 || toVersion <= 0) {
            throw new IllegalArgumentException("版本号必须为正数");
        }
        TrainingContentVersionHistoryPO from = findVersion(contentId, fromVersion);
        TrainingContentVersionHistoryPO to = findVersion(contentId, toVersion);
        List<String> changedFields = new java.util.ArrayList<>();
        if (!Objects.equals(from.getTitle(), to.getTitle())) changedFields.add("title");
        if (!Objects.equals(from.getContentType(), to.getContentType())) changedFields.add("contentType");
        if (!Objects.equals(from.getDifficulty(), to.getDifficulty())) changedFields.add("difficulty");
        if (!Objects.equals(from.getContentBody(), to.getContentBody())) changedFields.add("contentBody");
        if (!Objects.equals(from.getStatus(), to.getStatus())) changedFields.add("status");
        return TrainingContentVersionDiffResponse.builder()
                .contentId(contentId)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .changedFields(changedFields)
                .fromSnapshot(toVersionResponse(from))
                .toSnapshot(toVersionResponse(to))
                .build();
    }
    public TrainingContentImportRecordResponse findImportRecord(Long recordId) {
        TrainingContentImportRecordPO record = importRecordRepository.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("导入记录不存在: " + recordId);
        }
        return toImportResponse(record);
    }

    private TrainingContentVersionHistoryPO findVersion(Long contentId, Integer version) {
        return historyRepository.findByContentId(contentId).stream()
                .filter(history -> Objects.equals(history.getVersion(), version))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "训练内容版本不存在: contentId=" + contentId + ", version=" + version));
    }
    private void saveSnapshot(Long contentId, String sourceType, String sourceFileName) {
        try {
            TrainingContentEntity content = contentDomainService.requireContent(contentId);
            historyRepository.save(TrainingContentVersionHistoryPO.builder()
                    .contentId(content.getId())
                    .version(content.getVersion())
                    .title(content.getTitle())
                    .contentType(content.getContentType())
                    .difficulty(content.getDifficulty())
                    .contentBody(content.getContentBody())
                    .status(content.getStatus().name())
                    .sourceType(sourceType == null || sourceType.isBlank() ? "UPDATE" : sourceType)
                    .sourceFileName(sourceFileName)
                    .build());
        } catch (RuntimeException exception) {
            log.error("failed to save training content version snapshot, contentId={}", contentId, exception);
        }
    }

    private TrainingContentVersionResponse toVersionResponse(TrainingContentVersionHistoryPO history) {
        return TrainingContentVersionResponse.builder()
                .id(history.getId())
                .contentId(history.getContentId())
                .version(history.getVersion())
                .title(history.getTitle())
                .contentType(history.getContentType())
                .difficulty(history.getDifficulty())
                .contentBody(history.getContentBody())
                .status(history.getStatus())
                .sourceType(history.getSourceType())
                .sourceFileName(history.getSourceFileName())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private TrainingContentImportRecordResponse toImportResponse(TrainingContentImportRecordPO record) {
        return TrainingContentImportRecordResponse.builder()
                .id(record.getId())
                .contentId(record.getContentId())
                .fileName(record.getFileName())
                .contentType(record.getContentType())
                .fileSize(record.getFileSize())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .indexTaskId(record.getIndexTaskId())
                .createdAt(record.getCreatedAt())
                .completedAt(record.getCompletedAt())
                .build();
    }
}

