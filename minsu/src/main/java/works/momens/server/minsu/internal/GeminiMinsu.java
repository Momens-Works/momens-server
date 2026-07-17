package works.momens.server.minsu.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import works.momens.server.common.api.BusinessException;
import works.momens.server.minsu.Minsu;
import works.momens.server.minsu.MinsuErrorCode;
import works.momens.server.minsu.MinsuSignalContext;
import works.momens.server.minsu.MinsuTaskDraft;

/**
 * Vertex AI(Gemini) 기반 민수 구현.
 *
 * <p>Signal 컨텍스트(제목·근거)를 프롬프트로 만들어 Gemini에 보내고, draft는 JSON으로 받아 파싱·정규화합니다. role·priority는 태스크 생성이
 * 받는 허용값으로 강제하고, title은 비면 Signal 제목으로 되돌립니다(생성 실패가 아니라 값 위생 처리). Vertex 호출·응답 파싱이 실패하면 {@link
 * MinsuErrorCode#MINSU_GENERATION_FAILED}로 던집니다(하드 의존).
 */
@Slf4j
class GeminiMinsu implements Minsu {

  /** ADR-0011 draft role·priority 허용값. 모델이 벗어난 값을 주면 기본값으로 정규화한다. */
  private static final Set<String> ALLOWED_ROLES = Set.of("pm", "design", "backend", "frontend");

  private static final Set<String> ALLOWED_PRIORITIES = Set.of("low", "medium", "high");

  private static final String DEFAULT_ROLE = "pm";

  private static final String DEFAULT_PRIORITY = "medium";

  private static final int MAX_TITLE_LENGTH = 60;

  private static final int MAX_SUGGESTION_LENGTH = 200;

  private static final int DRAFT_MAX_TOKENS = 256;

  private static final int SUGGEST_MAX_TOKENS = 400;

  private static final String DRAFT_SYSTEM =
      """
      너는 Momens의 AI 동료 '민수'다. 개발 프로젝트에서 감지된 Signal(변화·리스크)을 태스크로 등록할 때
      쓸 초안을 만든다. 아래 Signal 제목과 근거를 보고 태스크 초안을 JSON 하나로만 답한다.
      형식: {"title": string, "role": one of ["pm","design","backend","frontend"], "priority": one of ["low","medium","high"]}
      - title: 실행 가능한 태스크 제목. 한국어, 공백 포함 40자 이내로 간결하게.
      - role: 이 일을 맡기 가장 적합한 직군 하나.
      - priority: 근거의 영향 범위로 판단한 우선순위.
      JSON 외의 설명·코드블록·마크다운은 절대 쓰지 않는다.
      """;

  private static final String SUGGEST_SYSTEM =
      """
      너는 Momens의 AI 동료 '민수'다. 개발 프로젝트에서 감지된 Signal을 사용자가 이해하고 다음 행동을 잡도록
      돕는다. 아래 Signal 제목과 근거를 보고, 지금 확인하거나 대응하면 좋은 점을 한국어 한두 문장으로 제안한다.
      담백하고 실무적인 존댓말로 쓰고, 근거에 없는 사실을 지어내지 않는다. 제안 문장만 답한다.
      """;

  private final Client client;
  private final String model;
  private final ObjectMapper objectMapper;

  GeminiMinsu(Client client, String model, ObjectMapper objectMapper) {
    this.client = client;
    this.model = model;
    this.objectMapper = objectMapper;
  }

  @Override
  public MinsuTaskDraft draftTask(MinsuSignalContext context) {
    String raw = generate(DRAFT_SYSTEM, buildUserPrompt(context), true);
    return parseDraft(raw, context.title());
  }

  /** 생성된 JSON을 draft로 파싱·정규화한다. role·priority는 허용값으로, title은 비면 Signal 제목으로 되돌린다. */
  MinsuTaskDraft parseDraft(String raw, String fallbackTitle) {
    JsonNode node = parseJson(raw);
    return new MinsuTaskDraft(
        normalizeTitle(text(node, "title"), fallbackTitle),
        normalizeChoice(text(node, "role"), ALLOWED_ROLES, DEFAULT_ROLE),
        normalizeChoice(text(node, "priority"), ALLOWED_PRIORITIES, DEFAULT_PRIORITY));
  }

  @Override
  public String suggest(MinsuSignalContext context) {
    String raw = generate(SUGGEST_SYSTEM, buildUserPrompt(context), false);
    String trimmed = raw.strip();
    return trimmed.length() > MAX_SUGGESTION_LENGTH
        ? trimmed.substring(0, MAX_SUGGESTION_LENGTH).strip()
        : trimmed;
  }

  private String generate(String system, String user, boolean json) {
    GenerateContentConfig.Builder config =
        GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(system)))
            .temperature(json ? 0.3f : 0.6f)
            .maxOutputTokens(json ? DRAFT_MAX_TOKENS : SUGGEST_MAX_TOKENS);
    if (json) {
      config.responseMimeType("application/json");
    }
    String text;
    try {
      GenerateContentResponse response = client.models.generateContent(model, user, config.build());
      text = response.text();
    } catch (RuntimeException e) {
      log.warn("minsu: Vertex 호출 실패 model={}", model, e);
      throw new BusinessException(
          MinsuErrorCode.MINSU_GENERATION_FAILED, Map.of("reason", "vertex_call_failed"));
    }
    if (text == null || text.isBlank()) {
      log.warn("minsu: Vertex 응답이 비어 있음 model={}", model);
      throw new BusinessException(
          MinsuErrorCode.MINSU_GENERATION_FAILED, Map.of("reason", "empty_response"));
    }
    return text;
  }

  private JsonNode parseJson(String raw) {
    String candidate = stripToJsonObject(raw);
    try {
      return objectMapper.readTree(candidate);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("minsu: draft JSON 파싱 실패 raw={}", raw, e);
      throw new BusinessException(
          MinsuErrorCode.MINSU_GENERATION_FAILED, Map.of("reason", "invalid_json"));
    }
  }

  /** responseMimeType이 무시되어 코드블록/설명이 섞여 와도 첫 JSON object만 뽑아 파싱을 견고하게 한다. */
  private static String stripToJsonObject(String raw) {
    String s = raw.strip();
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    return start >= 0 && end > start ? s.substring(start, end + 1) : s;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static String normalizeChoice(String value, Set<String> allowed, String fallback) {
    if (value == null) {
      return fallback;
    }
    String lower = value.strip().toLowerCase();
    return allowed.contains(lower) ? lower : fallback;
  }

  private static String normalizeTitle(String value, String signalTitle) {
    String title = value == null ? "" : value.strip();
    if (title.length() >= 2
        && ((title.startsWith("\"") && title.endsWith("\""))
            || (title.startsWith("'") && title.endsWith("'")))) {
      title = title.substring(1, title.length() - 1).strip();
    }
    if (title.isBlank()) {
      title = signalTitle == null ? "" : signalTitle.strip();
    }
    return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH).strip() : title;
  }

  private static String buildUserPrompt(MinsuSignalContext context) {
    StringBuilder sb = new StringBuilder();
    sb.append("Signal 제목: ").append(nullToDash(context.title())).append('\n');
    if (context.evidence().isEmpty()) {
      sb.append("근거: (없음)");
      return sb.toString();
    }
    sb.append("근거(대상 / 변화 / 영향):");
    for (MinsuSignalContext.Evidence e : context.evidence()) {
      sb.append("\n- ")
          .append(nullToDash(e.target()))
          .append(" / ")
          .append(nullToDash(e.change()))
          .append(" / ")
          .append(nullToDash(e.impact()));
    }
    return sb.toString();
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value.strip();
  }
}
