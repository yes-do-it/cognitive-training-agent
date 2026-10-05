package cn.xfone.ai.agent;

/**
 * A small protocol object used by the HTTP adapter to preserve the difference
 * between progress messages and model tokens in the Agent SSE stream.
 */
public record AgentStreamChunk(String type, String data) {
    public static AgentStreamChunk stage(String data) {
        return new AgentStreamChunk("stage", data);
    }

    public static AgentStreamChunk token(String data) {
        return new AgentStreamChunk("token", data);
    }

    public static AgentStreamChunk evidence(String data) {
        return new AgentStreamChunk("evidence", data);
    }
}
