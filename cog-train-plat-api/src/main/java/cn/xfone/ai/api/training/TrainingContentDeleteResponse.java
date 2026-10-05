package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingContentDeleteResponse {
    private Long contentId;
    private Boolean deleted;
    private Integer version;
}
