package cn.xfone.ai.domain.training.event;

public record TrainingContentCreatedEvent(Long contentId, String sourceType, String sourceFileName) {
}
