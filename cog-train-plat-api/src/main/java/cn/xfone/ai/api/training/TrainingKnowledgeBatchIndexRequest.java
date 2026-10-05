package cn.xfone.ai.api.training;

import lombok.Data;

import java.util.List;

@Data
public class TrainingKnowledgeBatchIndexRequest {
    private List<Long> contentIds;
}
