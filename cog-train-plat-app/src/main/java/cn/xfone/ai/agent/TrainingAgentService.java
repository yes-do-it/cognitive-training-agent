package cn.xfone.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "cognitive.agent.enabled", havingValue = "true")
public class TrainingAgentService {

    private static final ZoneId TRAINING_ZONE = ZoneId.of("Asia/Shanghai");

    private final ChatClient chatClient;
    private final TrainingAgentTools trainingAgentTools;
    private final TrainingKnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public TrainingAgentService(ChatClient cognitiveTrainingChatClient,
                                TrainingAgentTools trainingAgentTools,
                                TrainingKnowledgeService knowledgeService,
                                ObjectMapper objectMapper) {
        this.chatClient = cognitiveTrainingChatClient;
        this.trainingAgentTools = trainingAgentTools;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    public Flux<AgentStreamChunk> streamFlow(Long userId, String message, LocalDate trainingDate) {
        return stream(userId, message, trainingDate,
                "按固定流程处理：查询训练计划和任务 -> 分析历史上下文 -> 检索相关训练内容知识 -> 给出训练编排建议 -> 输出待确认方案。"
                        + "涉及修改计划或任务时，只能输出待确认方案，不得直接修改数据库。"
                        + "先用日常中文回答和解释分析结论，不要只返回 JSON；仅在确实需要生成训练计划时再输出结构化方案。"
                        + "如果需要编排训练计划，必须在回答末尾输出一个 ```json 代码块，字段严格为 name、startDate、endDate、scheduleTime、contentIds、reason；"
                        + "日期格式为 yyyy-MM-dd，时间格式为 HH:mm，contentIds 只能使用事实中出现的训练内容 ID，不确定时输出空数组。");
    }

    public Flux<AgentStreamChunk> streamAuto(Long userId, String message, LocalDate trainingDate) {
        return stream(userId, message, trainingDate,
                "你是认知训练辅助编排 Agent。先验证事实，再结合知识库内容回答用户问题；不得编造训练计划、任务状态或执行结果。"
                        + "先用日常中文回答和解释分析结论，不要只返回 JSON；仅在确实需要生成训练计划时再输出结构化方案。"
                        + "如果用户要求制定训练计划，回答末尾必须输出一个 ```json 代码块，字段严格为 name、startDate、endDate、scheduleTime、contentIds、reason；"
                        + "日期格式为 yyyy-MM-dd，时间格式为 HH:mm，contentIds 只能使用事实中出现的训练内容 ID，不确定时输出空数组。");
    }

    private Flux<AgentStreamChunk> stream(Long userId, String message, LocalDate trainingDate, String systemPrompt) {
        return Flux.concat(
                Mono.just(AgentStreamChunk.stage("已接收请求，开始读取训练上下文")),
                Flux.defer(() -> {
                    LocalDate effectiveDate = trainingDate == null ? LocalDate.now(TRAINING_ZONE) : trainingDate;
                    FactsSnapshot factsSnapshot = loadFacts(userId, effectiveDate);
                    return Flux.concat(
                            Mono.just(AgentStreamChunk.stage("训练计划、今日任务和历史执行记录读取完成")),
                            Mono.just(AgentStreamChunk.evidence(serializeTrend(factsSnapshot.stats()))),
                            Flux.defer(() -> {
                                String knowledge = loadKnowledge(message);
                                String prompt = "用户ID：" + userId
                                        + "\n目标训练日期：" + effectiveDate
                                        + "\n用户请求：" + message
                                        + "\n\n以下是系统刚刚通过只读训练工具查询到的事实，请仅基于这些事实分析，不要编造数据：\n"
                                        + factsSnapshot.serialized()
                                        + "\n\n以下是从认知训练知识库检索到的相关内容，仅可作为训练内容和难度建议依据：\n"
                                        + knowledge;
                                return Flux.concat(
                                        Mono.just(AgentStreamChunk.stage("相关训练内容知识检索完成，开始生成编排建议")),
                                        chatClient.prompt()
                                                .system(systemPrompt)
                                                .user(prompt)
                                                .stream()
                                                .content()
                                                .map(AgentStreamChunk::token));
                            }));
                }));
    }

    private FactsSnapshot loadFacts(Long userId, LocalDate today) {
        try {
            Map<String, Object> stats = trainingAgentTools.queryTrainingStats(
                    userId, today.minusDays(30).toString(), today.toString());
            String serialized = objectMapper.writeValueAsString(new Facts(
                    trainingAgentTools.queryTrainingContext(userId, today.toString()),
                    trainingAgentTools.queryTrainingHistory(userId, today.minusDays(30).toString(), today.toString()),
                    stats,
                    trainingAgentTools.queryTrainingPlans(userId)));
            return new FactsSnapshot(serialized, stats);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("训练事实序列化失败", exception);
        }
    }

    private String serializeTrend(Map<String, Object> stats) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("totalExecutions", stats.getOrDefault("totalExecutions", 0));
            evidence.put("successCount", stats.getOrDefault("successCount", 0));
            evidence.put("successRate", stats.getOrDefault("successRate", 0D));
            evidence.put("averageScore", stats.getOrDefault("averageScore", 0D));
            evidence.put("averageDurationSeconds", stats.getOrDefault("averageDurationSeconds", 0D));
            evidence.put("contentPerformance", stats.getOrDefault("contentPerformance", List.of()));
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("训练趋势序列化失败", exception);
        }
    }

    private String loadKnowledge(String message) {
        List<Document> documents = knowledgeService.search(message);
        if (documents.isEmpty()) {
            return "未检索到匹配的训练内容知识。";
        }
        return documents.stream()
                .map(document -> Map.of(
                        "text", document.getText(),
                        "score", document.getScore() == null ? 0D : document.getScore(),
                        "metadata", document.getMetadata()))
                .toList()
                .toString();
    }

    private record Facts(Object context, Object history, Object stats, Object plans) {
    }

    private record FactsSnapshot(String serialized, Map<String, Object> stats) {
    }
}
