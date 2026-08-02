package works.momens.server.minsu.internal.google;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.internal.config.MinsuLlmProperties;

class MinsuContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(TestApplication.class);

  @Test
  void startsWithValidEnabledConfigAndTurnsClientFactoryFailureIntoFallback() {
    contextRunner
        .withPropertyValues(
            "momens.minsu.task-draft.enabled=true",
            "momens.minsu.llm.provider=google",
            "momens.minsu.llm.model=gemini-3.5-flash-lite",
            "momens.minsu.llm.google.project=test-project",
            "momens.minsu.llm.google.location=global")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              TaskDraft draft =
                  context
                      .getBean(SignalTaskDraftGenerator.class)
                      .generate(new SignalTaskDraftInput("시그널 제목", "risk", "설명", null, List.of()));

              assertThat(draft).isEqualTo(new TaskDraft("시그널 제목", Role.PM, Priority.MEDIUM));
              assertThat(context.getBean(FailingGoogleClientFactory.class).calls).hasValue(1);
              assertThat(context.getBean(MinsuLlmProperties.class).timeout())
                  .isEqualTo(Duration.ofSeconds(8));
            });
  }

  @ParameterizedTest
  @MethodSource("invalidConfiguration")
  void startsWithInvalidProviderModelOrLocationAndDoesNotCreateClient(
      String property, String value) {
    contextRunner
        .withPropertyValues(
            "momens.minsu.task-draft.enabled=true",
            "momens.minsu.llm.provider=google",
            "momens.minsu.llm.model=gemini-3.5-flash-lite",
            "momens.minsu.llm.google.project=test-project",
            "momens.minsu.llm.google.location=global",
            property + "=" + value)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              context
                  .getBean(SignalTaskDraftGenerator.class)
                  .generate(new SignalTaskDraftInput("시그널 제목", "risk", "설명", null, List.of()));

              assertThat(context.getBean(FailingGoogleClientFactory.class).calls).hasValue(0);
              assertThat(
                      context
                          .getBean(MeterRegistry.class)
                          .get("momens.minsu.llm.config.valid")
                          .gauge()
                          .value())
                  .isZero();
            });
  }

  private static Stream<Arguments> invalidConfiguration() {
    return Stream.of(
        Arguments.of("momens.minsu.llm.provider", "other"),
        Arguments.of("momens.minsu.llm.model", "other-model"),
        Arguments.of("momens.minsu.llm.timeout", "0s"),
        Arguments.of("momens.minsu.llm.google.location", "asia-northeast3"));
  }

  // 모듈의 빈은 모두 internal 하위에 있다. 루트 패키지까지 스캔하면 슬라이스 테스트용 부트스트랩
  // (MinsuModuleTestApplication)이 함께 잡혀 @EnableAutoConfiguration이 켜지고, DataSource 없이 도는
  // 이 컨텍스트에서 Flyway·JPA 자동 구성이 실패한다(MOM-0817에서 minsu가 영속성을 갖게 된 뒤).
  @Configuration(proxyBeanMethods = false)
  @ComponentScan("works.momens.server.minsu.internal")
  static class TestApplication {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ObservationRegistry observationRegistry() {
      return ObservationRegistry.create();
    }

    @Bean
    FailingGoogleClientFactory failingGoogleClientFactory() {
      return new FailingGoogleClientFactory();
    }

    @Bean
    @Primary
    GoogleClientFactory testGoogleClientFactory(FailingGoogleClientFactory factory) {
      return factory;
    }
  }

  static final class FailingGoogleClientFactory implements GoogleClientFactory {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public GoogleSdkClient create(works.momens.server.minsu.internal.llm.ModelSelection selection) {
      calls.incrementAndGet();
      throw new IllegalStateException("ADC unavailable");
    }
  }
}
