package cn.xfone.ai.agent;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingContentBatchImportResponse;
import cn.xfone.ai.api.training.TrainingContentImportResponse;
import cn.xfone.ai.api.training.TrainingKnowledgeIndexTaskResponse;
import cn.xfone.ai.domain.training.event.TrainingContentCreatedEvent;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import cn.xfone.ai.infrastructure.adapter.repository.TrainingContentImportRecordRepository;
import cn.xfone.ai.infrastructure.dao.po.TrainingContentImportRecordPO;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/training/contents")
public class TrainingContentImportController {
    private final TrainingContentDomainService contentDomainService;
    private final TrainingKnowledgeService knowledgeService;
    private final TrainingKnowledgeIndexTaskService indexTaskService;
    private final TrainingContentImportRecordRepository importRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TrainingContentImportController(TrainingContentDomainService contentDomainService,
                                           TrainingKnowledgeService knowledgeService,
                                           TrainingKnowledgeIndexTaskService indexTaskService,
                                           TrainingContentImportRecordRepository importRecordRepository,
                                           ApplicationEventPublisher eventPublisher) {
        this.contentDomainService = contentDomainService;
        this.knowledgeService = knowledgeService;
        this.indexTaskService = indexTaskService;
        this.importRecordRepository = importRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingContentImportResponse> importDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "difficulty", required = false) Integer difficulty,
            @RequestParam(value = "status", defaultValue = "ENABLED") String status,
            @RequestParam(value = "index", defaultValue = "true") boolean index) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        String resolvedContentType = resolveContentType(contentType, fileName);
        TrainingContentImportRecordPO importRecord = TrainingContentImportRecordPO.builder()
                .fileName(fileName)
                .contentType(resolvedContentType)
                .fileSize(file.getSize())
                .status("PROCESSING")
                .build();
        importRecordRepository.create(importRecord);

        try {
            String contentBody = extractText(file);
            String resolvedTitle = resolveTitle(title, fileName);
            TrainingContentEntity content = contentDomainService.createContent(TrainingContentEntity.builder()
                    .title(resolvedTitle)
                    .contentType(resolvedContentType)
                    .difficulty(difficulty)
                    .contentBody(contentBody)
                    .status(TrainingContentStatusVO.valueOf(status.toUpperCase(Locale.ROOT)))
                    .build());
            eventPublisher.publishEvent(new TrainingContentCreatedEvent(
                    content.getId(), "IMPORT", fileName));

            String taskId = null;
            boolean taskSubmitted = false;
            if (index && knowledgeService.enabled()) {
                TrainingKnowledgeIndexTaskResponse task = indexTaskService.submit(List.of(content.getId()));
                taskId = task.getTaskId();
                taskSubmitted = true;
            }
            importRecordRepository.markSucceeded(importRecord.getId(), content.getId(), taskId);

            return success(TrainingContentImportResponse.builder()
                    .importRecordId(importRecord.getId())
                    .contentId(content.getId())
                    .title(content.getTitle())
                    .contentType(content.getContentType())
                    .version(content.getVersion())
                    .indexTaskSubmitted(taskSubmitted)
                    .indexTaskId(taskId)
                    .build());
        } catch (RuntimeException exception) {
            markImportFailed(importRecord.getId(), exception);
            throw exception;
        }
    }

    @PostMapping(value = "/import/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingContentBatchImportResponse> importDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "difficulty", required = false) Integer difficulty,
            @RequestParam(value = "status", defaultValue = "ENABLED") String status,
            @RequestParam(value = "index", defaultValue = "true") boolean index) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("批量导入文件不能为空");
        }
        if (files.size() > 20) {
            throw new IllegalArgumentException("单次最多导入 20 个文件");
        }

        List<TrainingContentImportResponse> items = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            items.add(importDocument(file, null, contentType, difficulty, status, false).getData());
        }

        String taskId = null;
        boolean taskSubmitted = false;
        if (index && knowledgeService.enabled()) {
            TrainingKnowledgeIndexTaskResponse task = indexTaskService.submit(
                    items.stream().map(TrainingContentImportResponse::getContentId).toList());
            taskId = task.getTaskId();
            taskSubmitted = true;
            for (TrainingContentImportResponse item : items) {
                importRecordRepository.markSucceeded(
                        item.getImportRecordId(), item.getContentId(), taskId);
                item.setIndexTaskSubmitted(true);
                item.setIndexTaskId(taskId);
            }
        }

        return success(TrainingContentBatchImportResponse.builder()
                .totalCount(items.size())
                .successCount(items.size())
                .indexTaskSubmitted(taskSubmitted)
                .indexTaskId(taskId)
                .items(items)
                .build());
    }
    private void markImportFailed(Long importRecordId, RuntimeException exception) {
        String message = exception.getMessage();
        try {
            importRecordRepository.markFailed(importRecordId,
                    message == null || message.isBlank() ? "导入失败" : message);
        } catch (RuntimeException auditException) {
            auditException.addSuppressed(exception);
            throw auditException;
        }
    }

    private String extractText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename();
            Resource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            List<Document> documents = new TikaDocumentReader(resource).get();
            String content = documents.stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"))
                    .trim();
            if (content.isBlank()) {
                throw new IllegalArgumentException("文件未解析出有效文本");
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取导入文件失败", exception);
        }
    }

    private String resolveTitle(String title, String filename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (filename == null || filename.isBlank()) {
            return "导入训练内容";
        }
        String safeFilename = filename.replace('\\', '/');
        safeFilename = safeFilename.substring(safeFilename.lastIndexOf('/') + 1);
        int extensionIndex = safeFilename.lastIndexOf('.');
        return (extensionIndex > 0 ? safeFilename.substring(0, extensionIndex) : safeFilename).trim();
    }

    private String resolveContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toUpperCase(Locale.ROOT);
        }
        if (filename != null) {
            int extensionIndex = filename.lastIndexOf('.');
            if (extensionIndex > -1 && extensionIndex < filename.length() - 1) {
                return filename.substring(extensionIndex + 1).toUpperCase(Locale.ROOT);
            }
        }
        return "DOCUMENT";
    }

    private String normalizeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown-document";
        }
        String safeFilename = filename.replace('\\', '/');
        return safeFilename.substring(safeFilename.lastIndexOf('/') + 1);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}

