package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingContentCreateRequest;
import cn.xfone.ai.api.training.TrainingContentDeleteResponse;
import cn.xfone.ai.api.training.TrainingContentResponse;
import cn.xfone.ai.api.training.TrainingContentUpdateRequest;
import cn.xfone.ai.domain.training.event.TrainingContentCreatedEvent;
import cn.xfone.ai.domain.training.event.TrainingContentDeletedEvent;
import cn.xfone.ai.domain.training.event.TrainingContentUpdatedEvent;
import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingContentStatusVO;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training/contents")
public class TrainingContentController {
    private final TrainingContentDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    public TrainingContentController(TrainingContentDomainService domainService,
                                     ApplicationEventPublisher eventPublisher) {
        this.domainService = domainService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingContentResponse> create(
            @Valid @RequestBody TrainingContentCreateRequest request) {
        TrainingContentEntity content = domainService.createContent(TrainingContentEntity.builder()
                .title(request.getTitle()).contentType(request.getContentType())
                .difficulty(request.getDifficulty()).contentBody(request.getContentBody())
                .status(request.getStatus() == null
                        ? TrainingContentStatusVO.ENABLED
                        : TrainingContentStatusVO.valueOf(request.getStatus()))
                .build());
        eventPublisher.publishEvent(new TrainingContentCreatedEvent(content.getId(), "MANUAL", null));
        return success(toResponse(content));
    }

    @GetMapping
    public Response<java.util.List<TrainingContentResponse>> list() {
        return success(domainService.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{contentId}")
    public Response<TrainingContentResponse> find(@PathVariable("contentId") Long contentId) {
        return success(toResponse(domainService.requireContent(contentId)));
    }

    @PutMapping("/{contentId}")
    public Response<TrainingContentResponse> update(
            @PathVariable("contentId") Long contentId,
            @Valid @RequestBody TrainingContentUpdateRequest request) {
        TrainingContentEntity content = domainService.updateContent(TrainingContentEntity.builder()
                .id(contentId).title(request.getTitle()).contentType(request.getContentType())
                .difficulty(request.getDifficulty()).contentBody(request.getContentBody())
                .status(TrainingContentStatusVO.valueOf(request.getStatus()))
                .version(request.getExpectedVersion()).build());
        eventPublisher.publishEvent(new TrainingContentUpdatedEvent(content.getId(), content.getVersion()));
        return success(toResponse(content));
    }

    @DeleteMapping("/{contentId}")
    public Response<TrainingContentDeleteResponse> delete(@PathVariable("contentId") Long contentId) {
        TrainingContentEntity content = domainService.disableContent(contentId);
        eventPublisher.publishEvent(new TrainingContentDeletedEvent(content.getId(), content.getVersion()));
        return success(TrainingContentDeleteResponse.builder()
                .contentId(content.getId())
                .deleted(true)
                .version(content.getVersion())
                .build());
    }

    private TrainingContentResponse toResponse(TrainingContentEntity content) {
        return TrainingContentResponse.builder().id(content.getId()).title(content.getTitle())
                .contentType(content.getContentType()).difficulty(content.getDifficulty())
                .contentBody(content.getContentBody()).status(content.getStatus().name())
                .version(content.getVersion()).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}


