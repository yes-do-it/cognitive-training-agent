package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TrainingKnowledgeBatchIndexResponse {
    private Map<Long, Integer> chunkCounts;
    private Integer indexedCount;
}
