package works.momens.server.web.decision.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import works.momens.server.project.decision.DecisionReversibility;

/** 빈 문자열의 기존 기본값 의미만 처리하고 나머지는 표준 enum 검증에 위임합니다. */
class DecisionReversibilityDeserializer extends ValueDeserializer<DecisionReversibility> {
  @Override
  public DecisionReversibility deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.hasToken(JsonToken.VALUE_STRING) && parser.getString().isEmpty()) {
      return DecisionReversibility.REVERSIBLE;
    }
    return context.readValue(parser, DecisionReversibility.class);
  }
}
