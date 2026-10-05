package cn.xfone.ai.domain.training.adapter.repository;

import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;

public interface ITrainingUserRepository {
    void save(TrainingUserEntity user);

    TrainingUserEntity findById(Long userId);

    TrainingUserEntity findByExternalUserId(String externalUserId);

    int updateStatus(Long userId, TrainingUserStatusVO status);
}
