package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingContentResponse {
    private Long id;
    private String title;
    private String contentType;
    private Integer difficulty;
    private String contentBody;
    private String status;
    private Integer version;
}
