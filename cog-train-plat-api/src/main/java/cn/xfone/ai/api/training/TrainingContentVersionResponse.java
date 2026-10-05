package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TrainingContentVersionResponse {
    private Long id;
    private Long contentId;
    private Integer version;
    private String title;
    private String contentType;
    private Integer difficulty;
    private String contentBody;
    private String status;
    private String sourceType;
    private String sourceFileName;
    private LocalDateTime createdAt;
}
