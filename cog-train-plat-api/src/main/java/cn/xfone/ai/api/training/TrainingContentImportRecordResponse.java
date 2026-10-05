package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TrainingContentImportRecordResponse {
    private Long id;
    private Long contentId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private String indexTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
