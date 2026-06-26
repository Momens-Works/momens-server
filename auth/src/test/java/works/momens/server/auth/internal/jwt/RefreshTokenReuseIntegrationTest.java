package works.momens.server.auth.internal.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.auth.internal.refresh.ClientType;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 재사용 감지가 실제 트랜잭션 경계에서 동작하는지 검증한다.
 *
 * <p>단위 테스트(InMemory store)는 트랜잭션 롤백을 모델링하지 못해, 재사용 폐기가 회전 메서드의 {@code BusinessException} 롤백에 휩쓸리는
 * 문제를 못 잡는다. 여기서는 실제 JPA 스토어 + 독립 트랜잭션으로 세션 폐기가 커밋되는지 확인한다.
 *
 * <p>{@code @SpringBootTest}는 테스트 메서드를 트랜잭션으로 감싸지 않으므로(각 서비스 호출이 자기 트랜잭션을 커밋·재조회) 1차 캐시 staleness
 * 없이 REQUIRES_NEW 커밋을 관찰할 수 있다.
 */
@SpringBootTest(
    classes = RefreshTokenReuseIntegrationTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RefreshTokenReuseIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JwtTokenService jwtTokenService;

  @Test
  void reusingRevokedRefreshTokenRevokesTheRotatedSession() {
    UUID userId = UUID.randomUUID();
    TokenPair first = jwtTokenService.issueTokenPair(userId, ClientType.MOBILE, "iPhone");
    TokenPair rotated = jwtTokenService.refresh(first.refreshToken());

    // 폐기된 옛 refresh token 재사용 → 재사용 감지로 거부.
    assertThatThrownBy(() -> jwtTokenService.refresh(first.refreshToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));

    // 재사용 감지로 활성 rotated token까지 폐기돼야 한다. REQUIRES_NEW가 아니면 폐기가 롤백돼 이 호출이 통과한다.
    assertThatThrownBy(() -> jwtTokenService.refresh(rotated.refreshToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(
      basePackages = {
        "works.momens.server.auth.internal.jwt",
        "works.momens.server.auth.internal.refresh"
      })
  @EntityScan(basePackages = "works.momens.server.auth.internal.refresh")
  @EnableJpaRepositories(basePackages = "works.momens.server.auth.internal.refresh")
  @Import(JpaAuditingConfig.class)
  static class Config {}
}
