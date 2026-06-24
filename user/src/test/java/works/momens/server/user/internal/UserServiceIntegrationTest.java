package works.momens.server.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * user public API의 실제 DB(Testcontainers) 동작 검증.
 *
 * <p>{@code findOrCreate} upsert, 프로필 수정, not-found 에러를 Flyway 스키마 위에서 확인합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, UserServiceImpl.class})
class UserServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserService userService;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findOrCreateCreatesNewUser() {
    UserProfile created = userService.findOrCreate("new@momens.works", "규일", "https://a/x.png");

    assertThat(created.id()).isNotNull();
    assertThat(created.email()).isEqualTo("new@momens.works");
    assertThat(created.name()).isEqualTo("규일");
    assertThat(created.avatarUrl()).isEqualTo("https://a/x.png");
    assertThat(created.jobRole()).isNull();
  }

  @Test
  void findOrCreateUpsertsExistingByEmailAndRefreshesProfile() {
    UserProfile first = userService.findOrCreate("same@momens.works", "이전", null);

    // 실제 로그인은 호출마다 별도 트랜잭션이다. 두 번째 조회가 1차 캐시의 이전 엔티티가 아니라
    // upsert로 갱신된 DB 값을 보도록 영속성 컨텍스트 경계를 명시적으로 끊는다.
    entityManager.flush();
    entityManager.clear();

    UserProfile second = userService.findOrCreate("same@momens.works", "변경", "https://a/y.png");

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.name()).isEqualTo("변경");
    assertThat(second.avatarUrl()).isEqualTo("https://a/y.png");
  }

  @Test
  void updateProfileChangesProvidedFieldsOnly() {
    UUID id = userService.findOrCreate("edit@momens.works", "원래", null).id();

    UserProfile updated = userService.updateProfile(id, "새이름", "Engineer");
    assertThat(updated.name()).isEqualTo("새이름");
    assertThat(updated.jobRole()).isEqualTo("Engineer");

    UserProfile nameKept = userService.updateProfile(id, null, "Designer");
    assertThat(nameKept.name()).isEqualTo("새이름");
    assertThat(nameKept.jobRole()).isEqualTo("Designer");
  }

  @Test
  void getProfileThrowsWhenMissing() {
    assertThatThrownBy(() -> userService.getProfile(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(UserErrorCode.USER_NOT_FOUND);
  }
}
