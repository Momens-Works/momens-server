package works.momens.server.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * user 모듈의 public API.
 *
 * <p>다른 모듈(로그인 시 {@code auth})은 이 인터페이스로만 사용자를 다룹니다. 동기 반환값이 필요한 협력이라 application event가 아니라 public
 * API 직접 참조로 둡니다(docs/design/module-map.md). 엔티티·repository는 모듈 {@code internal}에 숨깁니다.
 */
public interface UserService {

  /**
   * 검증된 외부 신원 정보로 email 기준 upsert.
   *
   * <p>기존 사용자는 name/avatar를 최신값으로 갱신하고, 없으면 생성합니다. email 검증(email_verified) 게이트는 호출자({@code auth})가
   * 책임지고, 여기서는 검증된 정보로 upsert만 수행합니다.
   */
  UserProfile findOrCreate(String email, String name, String avatarUrl);

  /**
   * email로 사용자 프로필을 조회합니다. 없으면 빈 {@link Optional}을 돌려줍니다. 읽기만 하므로 {@link #findOrCreate}와 달리 기존 프로필을
   * 바꾸지 않습니다.
   */
  Optional<UserProfile> findByEmail(String email);

  /** 사용자 프로필 조회. 없으면 {@link UserErrorCode#USER_NOT_FOUND}. */
  UserProfile getProfile(UUID userId);

  /**
   * 여러 사용자의 프로필을 한 번에 조회합니다. 멤버 목록처럼 다른 모듈이 가진 userId 목록에 프로필을 결합할 때 사용합니다(MOM-61).
   *
   * <p>없는 id는 에러 없이 결과에서 빠지고(반환 크기는 입력 이하), 반환 순서는 보장되지 않습니다. 순서가 필요하면 호출하는 쪽에서 정렬합니다.
   */
  List<UserProfile> getProfiles(Collection<UUID> userIds);

  /** 프로필 부분 수정. {@code null} 필드는 변경하지 않습니다. 없으면 {@link UserErrorCode#USER_NOT_FOUND}. */
  UserProfile updateProfile(UUID userId, String name, String jobRole);
}
