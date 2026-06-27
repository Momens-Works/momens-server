package works.momens.server.common.api;

import java.security.Principal;
import java.util.UUID;

/**
 * 보호 API에서 현재 사용자 식별자(userId)를 읽는 공유 헬퍼.
 *
 * <p>도메인 컨트롤러는 auth 내부(JWT 디코더·쿠키·필터)를 모른 채 {@code Principal}만 받아 호출합니다. {@code
 * Principal.getName()}이 userId(UUID)라는 계약과 인증 수단(Bearer/쿠키) 중립성은 auth 모듈이 보장합니다.
 */
public final class CurrentUser {

  private CurrentUser() {}

  /**
   * 현재 사용자 userId.
   *
   * @throws BusinessException 인증 정보가 없으면 {@link CommonErrorCode#AUTH_UNAUTHORIZED}, principal 이름이
   *     UUID가 아니면 {@link CommonErrorCode#AUTH_INVALID_TOKEN}.
   */
  public static UUID id(Principal principal) {
    if (principal == null) {
      throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
    }
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BusinessException(CommonErrorCode.AUTH_INVALID_TOKEN);
    }
  }
}
