package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingContentImportResponse {
    private Long importRecordId;
    private Long contentId;
    private String title;
    private String contentType;
    private Integer version;
    private Boolean indexTaskSubmitted;
    private String indexTaskId;
}

