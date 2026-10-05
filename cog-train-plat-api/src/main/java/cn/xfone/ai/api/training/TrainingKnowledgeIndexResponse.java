package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingKnowledgeIndexResponse {
    private Long contentId;
    private Integer chunkCount;
    private Boolean indexed;
}
