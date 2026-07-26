package works.momens.server.minsu.internal.llm;

public record LlmRequest(String promptVersion, String systemInstruction, String dataJson) {}
