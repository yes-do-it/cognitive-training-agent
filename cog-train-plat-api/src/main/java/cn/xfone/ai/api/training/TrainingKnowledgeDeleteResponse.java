package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingKnowledgeDeleteResponse {
    private Long contentId;
    private Boolean deleted;
}
