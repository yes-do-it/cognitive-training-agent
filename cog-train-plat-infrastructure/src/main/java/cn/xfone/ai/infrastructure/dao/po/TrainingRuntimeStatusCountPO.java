package cn.xfone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRuntimeStatusCountPO {
    private String status;
    private Integer itemCount;
}
