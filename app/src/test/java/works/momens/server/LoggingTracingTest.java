package works.momens.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 로그 상관관계 배선 검증.
 *
 * <p>Micrometer Tracing 브리지가 자동 구성되어 {@link Tracer} 빈이 존재하고, span이 scope에 들어오면 {@code
 * traceId}/{@code spanId}가 MDC에 채워지는지 확인합니다. 브리지 의존성이 빠지면 컨텍스트에 Tracer가 없어 실패하므로 회귀 가드 역할을 합니다.
 */
@SpringBootTest
class LoggingTracingTest extends AbstractPostgresIntegrationTest {

  @Autowired private Tracer tracer;

  @Test
  void tracerBeanIsWired() {
    assertThat(tracer).isNotNull();
  }

  @Test
  void traceIdIsPropagatedToMdcWithinSpanScope() {
    Span span = tracer.nextSpan().start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      assertThat(MDC.get("traceId")).isEqualTo(span.context().traceId());
      assertThat(MDC.get("spanId")).isEqualTo(span.context().spanId());
    } finally {
      span.end();
    }
  }
}
