package cn.xfone.ai.domain.training.model.entity;

import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingUserEntity {
    private Long id;
    private String externalUserId;
    private String nickname;
    private TrainingUserStatusVO status;
    private String timezone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
