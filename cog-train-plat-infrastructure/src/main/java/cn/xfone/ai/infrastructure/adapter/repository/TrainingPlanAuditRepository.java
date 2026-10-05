package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanAuditRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingPlanAuditEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingPlanAuditActionVO;
import cn.xfone.ai.infrastructure.dao.TrainingPlanAuditDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingPlanAuditPO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TrainingPlanAuditRepository implements ITrainingPlanAuditRepository {
    private final TrainingPlanAuditDao auditDao;

    public TrainingPlanAuditRepository(TrainingPlanAuditDao auditDao) {
        this.auditDao = auditDao;
    }

    @Override
    public void save(TrainingPlanAuditEntity audit) {
        TrainingPlanAuditPO po = TrainingPlanAuditPO.builder()
                .id(audit.getId())
                .planId(audit.getPlanId())
                .userId(audit.getUserId())
                .action(audit.getAction().name())
                .reason(audit.getReason())
                .snapshotData(audit.getSnapshotData())
                .createdAt(audit.getCreatedAt())
                .build();
        auditDao.insert(po);
        audit.setId(po.getId());
    }

    @Override
    public List<TrainingPlanAuditEntity> findByPlanId(Long planId) {
        return auditDao.findByPlanId(planId).stream().map(this::toEntity).toList();
    }

    private TrainingPlanAuditEntity toEntity(TrainingPlanAuditPO po) {
        return TrainingPlanAuditEntity.builder()
                .id(po.getId())
                .planId(po.getPlanId())
                .userId(po.getUserId())
                .action(TrainingPlanAuditActionVO.valueOf(po.getAction()))
                .reason(po.getReason())
                .snapshotData(po.getSnapshotData())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
