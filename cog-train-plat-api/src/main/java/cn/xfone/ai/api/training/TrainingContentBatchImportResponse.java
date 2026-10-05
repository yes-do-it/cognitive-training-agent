package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrainingContentBatchImportResponse {
    private Integer totalCount;
    private Integer successCount;
    private Boolean indexTaskSubmitted;
    private String indexTaskId;
    private List<TrainingContentImportResponse> items;
}
