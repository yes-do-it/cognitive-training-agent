package cn.xfone.ai.agent;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingContentImportRecordResponse;
import cn.xfone.ai.api.training.TrainingContentVersionDiffResponse;
import cn.xfone.ai.api.training.TrainingContentVersionResponse;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/training")
public class TrainingContentAuditController {
    private final TrainingContentAuditService auditService;

    public TrainingContentAuditController(TrainingContentAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/contents/{contentId}/versions")
    public Response<List<TrainingContentVersionResponse>> versions(
            @PathVariable("contentId") Long contentId) {
        return success(auditService.findVersions(contentId));
    }

    @GetMapping("/contents/{contentId}/versions/diff")
    public Response<TrainingContentVersionDiffResponse> diff(
            @PathVariable("contentId") Long contentId,
            @RequestParam("fromVersion") Integer fromVersion,
            @RequestParam("toVersion") Integer toVersion) {
        return success(auditService.findDiff(contentId, fromVersion, toVersion));
    }
    @GetMapping("/imports/{importId}")
    public Response<TrainingContentImportRecordResponse> importRecord(
            @PathVariable("importId") Long importId) {
        return success(auditService.findImportRecord(importId));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}

