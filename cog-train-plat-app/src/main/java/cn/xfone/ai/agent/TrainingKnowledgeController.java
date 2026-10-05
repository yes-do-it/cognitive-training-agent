package cn.xfone.ai.agent;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.api.training.TrainingKnowledgeBatchIndexRequest;
import cn.xfone.ai.api.training.TrainingKnowledgeBatchIndexResponse;
import cn.xfone.ai.api.training.TrainingKnowledgeDeleteResponse;
import cn.xfone.ai.api.training.TrainingKnowledgeHitResponse;
import cn.xfone.ai.api.training.TrainingKnowledgeIndexResponse;
import cn.xfone.ai.api.training.TrainingKnowledgeIndexTaskResponse;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class TrainingKnowledgeController {
    private final TrainingKnowledgeService knowledgeService;
    private final TrainingKnowledgeIndexTaskService indexTaskService;

    public TrainingKnowledgeController(TrainingKnowledgeService knowledgeService,
                                       TrainingKnowledgeIndexTaskService indexTaskService) {
        this.knowledgeService = knowledgeService;
        this.indexTaskService = indexTaskService;
    }

    @PostMapping("/training-contents/{contentId}/index")
    public Response<TrainingKnowledgeIndexResponse> index(
            @PathVariable("contentId") Long contentId) {
        int chunkCount = knowledgeService.indexContent(contentId);
        return success(TrainingKnowledgeIndexResponse.builder()
                .contentId(contentId)
                .chunkCount(chunkCount)
                .indexed(true)
                .build());
    }

    @PostMapping("/training-contents/index")
    public Response<TrainingKnowledgeBatchIndexResponse> batchIndex(
            @RequestBody TrainingKnowledgeBatchIndexRequest request) {
        Map<Long, Integer> chunkCounts = knowledgeService.indexContents(request.getContentIds());
        return success(TrainingKnowledgeBatchIndexResponse.builder()
                .chunkCounts(chunkCounts)
                .indexedCount(chunkCounts.size())
                .build());
    }

    @PostMapping("/training-contents/index/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Response<TrainingKnowledgeIndexTaskResponse> asyncBatchIndex(
            @RequestBody TrainingKnowledgeBatchIndexRequest request) {
        return success(indexTaskService.submit(request.getContentIds()));
    }

    @GetMapping("/index-tasks/{taskId}")
    public Response<TrainingKnowledgeIndexTaskResponse> indexTask(
            @PathVariable("taskId") String taskId) {
        return success(indexTaskService.find(taskId));
    }

    @DeleteMapping("/training-contents/{contentId}/index")
    public Response<TrainingKnowledgeDeleteResponse> delete(
            @PathVariable("contentId") Long contentId) {
        knowledgeService.deleteContent(contentId);
        return success(TrainingKnowledgeDeleteResponse.builder()
                .contentId(contentId)
                .deleted(true)
                .build());
    }

    @GetMapping("/search")
    public Response<List<TrainingKnowledgeHitResponse>> search(
            @RequestParam("query") String query) {
        List<TrainingKnowledgeHitResponse> hits = knowledgeService.search(query).stream()
                .map(this::toResponse)
                .toList();
        return success(hits);
    }

    private TrainingKnowledgeHitResponse toResponse(Document document) {
        return TrainingKnowledgeHitResponse.builder()
                .text(document.getText())
                .score(document.getScore())
                .metadata(document.getMetadata())
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}
