package works.momens.server.project.decision;

import com.fasterxml.jackson.annotation.JsonValue;

/** decisions.reversibility의 CHECK 제약과 같은 값 집합입니다. */
public enum DecisionReversibility {
  REVERSIBLE("reversible"),
  IRREVERSIBLE("irreversible");

  private final String value;

  DecisionReversibility(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
