package cn.xfone.ai.monitoring;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingRuntimeMonitorResponse;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class TrainingRuntimeMonitorController {
    private final TrainingRuntimeMonitorService monitorService;

    public TrainingRuntimeMonitorController(TrainingRuntimeMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/overview")
    public Response<TrainingRuntimeMonitorResponse> overview(
            @RequestParam(value = "staleMinutes", required = false) Integer staleMinutes,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Response.<TrainingRuntimeMonitorResponse>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(monitorService.snapshot(staleMinutes, limit))
                .build();
    }
}
