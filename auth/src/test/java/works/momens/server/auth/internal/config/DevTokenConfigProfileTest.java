package works.momens.server.auth.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link DevTokenConfig}가 {@link DevOnly} 게이트 밖에서는 등록되지 않는지, 너무 짧은 시크릿은 설정 바인딩 단계에서 걸러지는지 확인합니다.
 *
 * <p>{@code prod} 단독뿐 아니라 {@code prod}가 dev 계열 프로필과 함께 활성인 조합(예: {@code prod,dev})과 프로필 미설정 환경에서도
 * 닫혀야 합니다(fail-closed). {@code @Profile} 배열은 OR라 이 조합을 못 막으므로 {@code !prod & (...)} 식을 씁니다. 정상 등록되는
 * dev 계열은 통합테스트({@code DevTokenIntegrationTest}, test 프로필)가 엔드포인트 동작으로 확인합니다.
 */
class DevTokenConfigProfileTest {

  private static final String VALID_SECRET = "test-only-momens-auth-dev-token-secret";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(DevTokenConfig.class)
          .withPropertyValues(
              "momens.auth.dev-token.secret=" + VALID_SECRET,
              "momens.auth.dev-token.allowed-emails=a@b.com");

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

  @ParameterizedTest
  @ValueSource(strings = {"dev", "local", "test"})
  void devTokenConfigAbsentWhenProdActiveWithDevProfile(String devProfile) {
    runner
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", devProfile))
        .run(context -> assertThat(context).doesNotHaveBean(DevTokenConfig.class));
  }

  @Test
  void secretShorterThan32FailsToBind() {
    new ApplicationContextRunner()
        .withUserConfiguration(PropsOnlyConfig.class)
        .withPropertyValues(
            "momens.auth.dev-token.secret=too-short",
            "momens.auth.dev-token.allowed-emails=a@b.com")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(DevTokenProperties.class)
  static class PropsOnlyConfig {}
}
