package cn.xfone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingUserPO {
    private Long id;
    private String externalUserId;
    private String nickname;
    private String status;
    private String timezone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
