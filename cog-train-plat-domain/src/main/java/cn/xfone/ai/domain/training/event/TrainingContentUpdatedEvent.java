package cn.xfone.ai.domain.training.event;

public record TrainingContentUpdatedEvent(Long contentId, Integer version) {
}
