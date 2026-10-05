package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TrainingKnowledgeHitResponse {
    private String text;
    private Double score;
    private Map<String, Object> metadata;
}
