package works.momens.server.user;

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

  /** 사용자 프로필 조회. 없으면 {@link UserErrorCode#USER_NOT_FOUND}. */
  UserProfile getProfile(UUID userId);

  /** 프로필 부분 수정. {@code null} 필드는 변경하지 않습니다. 없으면 {@link UserErrorCode#USER_NOT_FOUND}. */
  UserProfile updateProfile(UUID userId, String name, String jobRole);
}
