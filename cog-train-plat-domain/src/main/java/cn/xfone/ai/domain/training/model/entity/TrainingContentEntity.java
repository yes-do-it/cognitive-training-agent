package cn.xfone.ai.domain.training.model.entity;

import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingContentEntity {
    private Long id;
    private String title;
    private String contentType;
    private Integer difficulty;
    private String contentBody;
    private TrainingContentStatusVO status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
