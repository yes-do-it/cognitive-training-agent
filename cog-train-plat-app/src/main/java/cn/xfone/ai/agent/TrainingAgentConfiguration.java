package cn.xfone.ai.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cognitive.agent.enabled", havingValue = "true")
public class TrainingAgentConfiguration {

    @Bean
    public ChatClient cognitiveTrainingChatClient(
            ChatClient.Builder builder,
            TrainingAgentTools localTools,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbacks) {
        ChatClient.Builder configured = builder.defaultSystem("""
                你是认知训练辅助编排 Agent。
                你只能通过提供的训练查询工具获取事实，不能编造训练计划、任务状态或执行结果。
                当用户要求分析训练情况时，先查询训练上下文，再给出可解释的建议。
                涉及修改计划或任务时，只能输出待确认方案，不得直接修改数据库。
                先用日常中文回答和解释分析结论，不要只返回 JSON；只有需要生成计划时，才在回答末尾附加结构化 JSON 供界面提取。
                """);

        SyncMcpToolCallbackProvider externalTools = mcpToolCallbacks.getIfAvailable();
        if (externalTools != null && externalTools.getToolCallbacks().length > 0) {
            return configured.defaultToolCallbacks(externalTools.getToolCallbacks()).build();
        }
        return configured.defaultTools(localTools).build();
    }
}
