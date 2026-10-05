package cn.xfone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingContentImportRecordPO {
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
