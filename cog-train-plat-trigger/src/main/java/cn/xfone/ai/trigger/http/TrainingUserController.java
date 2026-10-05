package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingUserCreateRequest;
import cn.xfone.ai.api.training.TrainingUserResponse;
import cn.xfone.ai.api.training.TrainingUserStatusRequest;
import cn.xfone.ai.domain.training.model.entity.TrainingUserEntity;
import cn.xfone.ai.domain.training.model.valobj.TrainingUserStatusVO;
import cn.xfone.ai.domain.training.service.TrainingUserDomainService;
import cn.xfone.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training/users")
public class TrainingUserController {
    private final TrainingUserDomainService domainService;

    public TrainingUserController(TrainingUserDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<TrainingUserResponse> create(@Valid @RequestBody TrainingUserCreateRequest request) {
        TrainingUserEntity user = domainService.createUser(TrainingUserEntity.builder()
                .externalUserId(request.getExternalUserId())
                .nickname(request.getNickname())
                .timezone(request.getTimezone())
                .build());
        return success(toResponse(user));
    }

    @GetMapping("/{userId}")
    public Response<TrainingUserResponse> find(@PathVariable("userId") Long userId) {
        return success(toResponse(domainService.requireUser(userId)));
    }

    @PutMapping("/{userId}/status")
    public Response<TrainingUserResponse> updateStatus(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody TrainingUserStatusRequest request) {
        TrainingUserEntity user = domainService.updateStatus(
                userId, TrainingUserStatusVO.valueOf(request.getStatus()));
        return success(toResponse(user));
    }

    private TrainingUserResponse toResponse(TrainingUserEntity user) {
        return TrainingUserResponse.builder().id(user.getId())
                .externalUserId(user.getExternalUserId()).nickname(user.getNickname())
                .status(user.getStatus().name()).timezone(user.getTimezone()).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}
