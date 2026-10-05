package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrainingContentVersionDiffResponse {
    private Long contentId;
    private Integer fromVersion;
    private Integer toVersion;
    private List<String> changedFields;
    private TrainingContentVersionResponse fromSnapshot;
    private TrainingContentVersionResponse toSnapshot;
}
