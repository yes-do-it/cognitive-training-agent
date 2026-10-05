package cn.xfone.ai.monitoring;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingHealthResponse;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class TrainingHealthController {
    private final TrainingHealthService healthService;

    public TrainingHealthController(TrainingHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/overview")
    public Response<TrainingHealthResponse> overview() {
        return Response.<TrainingHealthResponse>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(healthService.check())
                .build();
    }
}
