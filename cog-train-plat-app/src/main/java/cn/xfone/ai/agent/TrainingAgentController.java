package cn.xfone.ai.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/agent")
@ConditionalOnProperty(name = "cognitive.agent.enabled", havingValue = "true")
public class TrainingAgentController {

    private final TrainingAgentService trainingAgentService;

    public TrainingAgentController(TrainingAgentService trainingAgentService) {
        this.trainingAgentService = trainingAgentService;
    }

    @GetMapping(value = "/flow/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> flowStream(
            @RequestParam("userId") Long userId,
            @RequestParam("message") String message,
            @RequestParam(value = "trainingDate", required = false) LocalDate trainingDate) {
        return streamResponse(trainingAgentService.streamFlow(userId, message, trainingDate));
    }

    @GetMapping(value = "/auto/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> autoStream(
            @RequestParam("userId") Long userId,
            @RequestParam("message") String message,
            @RequestParam(value = "trainingDate", required = false) LocalDate trainingDate) {
        return streamResponse(trainingAgentService.streamAuto(userId, message, trainingDate));
    }

    @PostMapping(value = "/flow/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> flowStreamPost(@RequestBody AgentStreamRequest request) {
        return streamResponse(trainingAgentService.streamFlow(request.userId(), request.message(), request.trainingDate()));
    }

    @PostMapping(value = "/auto/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> autoStreamPost(@RequestBody AgentStreamRequest request) {
        return streamResponse(trainingAgentService.streamAuto(request.userId(), request.message(), request.trainingDate()));
    }
    private record AgentStreamRequest(Long userId, String message, LocalDate trainingDate) {
    }

    private ResponseEntity<Flux<ServerSentEvent<String>>> streamResponse(Flux<AgentStreamChunk> content) {
        Flux<ServerSentEvent<String>> events = content
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event(chunk.type())
                        .data(chunk.data())
                        .build())
                .concatWithValues(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(error.getMessage() == null ? "Agent 执行失败" : error.getMessage())
                        .build()));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .header("X-Content-Type-Options", "nosniff")
                .body(events);
    }
}
