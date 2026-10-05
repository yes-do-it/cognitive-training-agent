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
public class TrainingContentVersionHistoryPO {
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
