package cn.xfone.ai.api.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户确认 Agent 建议后的结构化落库请求。
 * Agent 只生成建议文本，客户端确认后才提交该请求。
 */
@Data
public class TrainingPlanConfirmRequest {
    @NotNull
    private Long userId;

    @NotBlank
    private String name;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private LocalTime scheduleTime;

    private Boolean enabled = true;

    @NotEmpty
    @Size(max = 20)
    @Valid
    private List<TrainingPlanContentRequest> contents = new ArrayList<>();
}
