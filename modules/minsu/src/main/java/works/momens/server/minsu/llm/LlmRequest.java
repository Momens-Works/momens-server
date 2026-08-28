package works.momens.server.minsu.llm;

public record LlmRequest(String promptVersion, String systemInstruction, String dataJson) {}
