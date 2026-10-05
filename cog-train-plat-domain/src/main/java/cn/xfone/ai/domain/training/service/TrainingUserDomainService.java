package cn.xfone.ai.domain.training.service;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingUserRepository;
import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;

public class TrainingUserDomainService {
    private final ITrainingUserRepository userRepository;

    public TrainingUserDomainService(ITrainingUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public TrainingUserEntity createUser(TrainingUserEntity user) {
        if (user.getExternalUserId() == null || user.getExternalUserId().isBlank()) {
            throw new IllegalArgumentException("外部用户ID不能为空");
        }
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            throw new IllegalArgumentException("用户昵称不能为空");
        }
        if (userRepository.findByExternalUserId(user.getExternalUserId()) != null) {
            throw new IllegalArgumentException("外部用户ID已存在");
        }
        user.setStatus(TrainingUserStatusVO.ACTIVE);
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            user.setTimezone("Asia/Shanghai");
        }
        userRepository.save(user);
        return requireUser(user.getId());
    }

    public TrainingUserEntity requireUser(Long userId) {
        TrainingUserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("训练用户不存在: " + userId);
        }
        return user;
    }

    public TrainingUserEntity updateStatus(Long userId, TrainingUserStatusVO status) {
        requireUser(userId);
        userRepository.updateStatus(userId, status);
        return requireUser(userId);
    }
}
