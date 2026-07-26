package works.momens.server.minsu.internal.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Prompt data와 structured output에만 사용하는 전용 JSON codec. */
@Component
public final class MinsuJson {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Minsu JSON 직렬화에 실패했습니다", e);
    }
  }

  public <T> Optional<T> read(String value, Class<T> type) {
    try {
      return Optional.of(objectMapper.readValue(value, type));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }
}
