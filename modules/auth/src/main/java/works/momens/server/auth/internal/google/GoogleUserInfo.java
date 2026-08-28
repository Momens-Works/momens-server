package works.momens.server.auth.internal.google;

/**
 * 검증이 완료된 Google 계정 정보.
 *
 * <p>로그인 시에는 {@code sub}을 사용자를 식별하는 값으로 사용합니다(ADR-0016). 나머지 값은 프로필 표시용으로 사용합니다.
 */
public record GoogleUserInfo(String sub, String email, String name, String picture) {}
