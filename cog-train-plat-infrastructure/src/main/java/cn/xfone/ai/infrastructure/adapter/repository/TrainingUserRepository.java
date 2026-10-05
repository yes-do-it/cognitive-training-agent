package cn.xfone.ai.infrastructure.adapter.repository;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingUserRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;
import cn.xfone.ai.infrastructure.dao.TrainingUserDao;
import cn.xfone.ai.infrastructure.dao.po.TrainingUserPO;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingUserRepository implements ITrainingUserRepository {
    private final TrainingUserDao userDao;

    public TrainingUserRepository(TrainingUserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void save(TrainingUserEntity user) {
        TrainingUserPO po = toPO(user);
        userDao.insert(po);
        user.setId(po.getId());
    }

    @Override
    public TrainingUserEntity findById(Long userId) {
        return toEntity(userDao.findById(userId));
    }

    @Override
    public TrainingUserEntity findByExternalUserId(String externalUserId) {
        return toEntity(userDao.findByExternalUserId(externalUserId));
    }

    @Override
    public int updateStatus(Long userId, TrainingUserStatusVO status) {
        return userDao.updateStatus(userId, status.name());
    }

    private TrainingUserPO toPO(TrainingUserEntity user) {
        return TrainingUserPO.builder()
                .id(user.getId())
                .externalUserId(user.getExternalUserId())
                .nickname(user.getNickname())
                .status(user.getStatus().name())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private TrainingUserEntity toEntity(TrainingUserPO po) {
        if (po == null) return null;
        return TrainingUserEntity.builder()
                .id(po.getId())
                .externalUserId(po.getExternalUserId())
                .nickname(po.getNickname())
                .status(TrainingUserStatusVO.valueOf(po.getStatus()))
                .timezone(po.getTimezone())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
