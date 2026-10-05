package cn.xfone.ai.agent;

import cn.xfone.ai.domain.training.event.TrainingTaskAdjustedEvent;
import cn.xfone.ai.infrastructure.dao.TrainingTaskAdjustmentAuditDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingTaskAdjustmentAuditPO;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class TrainingTaskAdjustmentAuditService {
    private final TrainingTaskAdjustmentAuditDao auditDao;

    public TrainingTaskAdjustmentAuditService(TrainingTaskAdjustmentAuditDao auditDao) {
        this.auditDao = auditDao;
    }

    @EventListener
    public void onTaskAdjusted(TrainingTaskAdjustedEvent event) {
        auditDao.insert(TrainingTaskAdjustmentAuditPO.builder()
                .taskId(event.taskId())
                .userId(event.userId())
                .trainingDate(event.trainingDate())
                .oldContentId(event.oldContentId())
                .newContentId(event.newContentId())
                .operatorId(event.operatorId())
                .reason(event.reason())
                .build());
    }
}