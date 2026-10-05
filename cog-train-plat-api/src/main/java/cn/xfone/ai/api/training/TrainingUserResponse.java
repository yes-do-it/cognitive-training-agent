package cn.xfone.ai.api.training;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingUserResponse {
    private Long id;
    private String externalUserId;
    private String nickname;
    private String status;
    private String timezone;
}
