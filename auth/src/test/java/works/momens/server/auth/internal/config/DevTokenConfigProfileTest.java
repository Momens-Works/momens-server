package works.momens.server.auth.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link DevTokenConfig}가 {@link DevOnly} allowlist 밖에서는 등록되지 않는지 확인합니다.
 *
 * <p>prod뿐 아니라 프로필 미설정 환경에서도 닫혀야 합니다(fail-closed). 이게 denylist인 {@code @Profile("!prod")} 대신
 * allowlist를 쓴 이유입니다. 등록되는 dev 계열은 통합테스트({@code DevTokenIntegrationTest}, test 프로필)가 엔드포인트 동작으로
 * 확인합니다.
 */
class DevTokenConfigProfileTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(DevTokenConfig.class)
          .withPropertyValues(
              "momens.auth.dev-token.secret=x", "momens.auth.dev-token.allowed-emails=a@b.com");

  @Test
  void devTokenConfigAbsentUnderProdProfile() {
    runner
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
        .run(context -> assertThat(context).doesNotHaveBean(DevTokenConfig.class));
  }

  @Test
  void devTokenConfigAbsentWhenNoProfileActive() {
    runner.run(context -> assertThat(context).doesNotHaveBean(DevTokenConfig.class));
  }
}
