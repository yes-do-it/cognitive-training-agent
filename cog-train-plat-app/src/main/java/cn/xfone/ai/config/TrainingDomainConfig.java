package cn.xfone.ai.config;

import cn.xfone.ai.domain.training.adapter.repository.ITrainingContentRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingPlanRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskExecutionRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingTaskRepository;
import cn.xfone.ai.domain.training.adapter.repository.ITrainingUserRepository;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import cn.xfone.ai.domain.training.service.TrainingExecutionDomainService;
import cn.xfone.ai.domain.training.service.TrainingPlanDomainService;
import cn.xfone.ai.domain.training.service.TrainingUserDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrainingDomainConfig {
    @Bean
    public TrainingUserDomainService trainingUserDomainService(ITrainingUserRepository repository) {
        return new TrainingUserDomainService(repository);
    }

    @Bean
    public TrainingContentDomainService trainingContentDomainService(ITrainingContentRepository repository) {
        return new TrainingContentDomainService(repository);
    }

    @Bean
    public TrainingPlanDomainService trainingPlanDomainService(
            ITrainingPlanRepository planRepository,
            ITrainingTaskRepository taskRepository,
            ITrainingUserRepository userRepository,
            ITrainingContentRepository contentRepository) {
        return new TrainingPlanDomainService(
                planRepository, taskRepository, userRepository, contentRepository);
    }

    @Bean
    public TrainingExecutionDomainService trainingExecutionDomainService(
            ITrainingTaskRepository taskRepository,
            ITrainingTaskExecutionRepository executionRepository) {
        return new TrainingExecutionDomainService(taskRepository, executionRepository);
    }
}
