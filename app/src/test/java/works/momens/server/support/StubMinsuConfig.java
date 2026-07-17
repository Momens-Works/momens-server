package works.momens.server.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import works.momens.server.minsu.Minsu;
import works.momens.server.minsu.MinsuSignalContext;
import works.momens.server.minsu.MinsuTaskDraft;

/**
 * 통합 테스트용 민수 스텁.
 *
 * <p>test 프로필은 {@code momens.minsu.enabled=false}라 실 배선은 {@code MINSU_UNAVAILABLE}을 던지므로, Vertex를
 * 호출하지 않는 결정적 스텁으로 대체합니다. convert-to-task 경로를 실배선으로 검증하는 {@code @SpringBootTest}가 {@code @Import}해
 * 씁니다. draft title은 Signal 제목을 그대로 써서 기존 계약(제목 보존)을 유지합니다.
 */
@TestConfiguration
public class StubMinsuConfig {

  @Bean
  @Primary
  Minsu stubMinsu() {
    return new Minsu() {
      @Override
      public MinsuTaskDraft draftTask(MinsuSignalContext context) {
        return new MinsuTaskDraft(context.title(), "pm", "medium");
      }

      @Override
      public String suggest(MinsuSignalContext context) {
        return "테스트 제안";
      }
    };
  }
}
