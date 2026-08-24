package works.momens.server.source.connection.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레거시와 OAuth state 서명 방식이 호환되는지 검증합니다.
 *
 * <p>테스트 입력값은 레거시 코드를 실제로 실행해 얻은 값입니다. 전환 기간에는 한쪽 서버가 발급한 state를 다른 서버가 검증해야 하므로 이 호환성 문제가 발생하면
 * source 연결 흐름이 중단됩니다. 테스트에서는 시각을 고정해 만료 동작도 함께 검증합니다.
 */
class OAuthStateSignerTest {

  private static final String SECRET = "shared-state-secret-for-cross-impl";
  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000012");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final Instant LEGACY_ISSUED_AT = Instant.parse("2026-08-21T00:00:00Z");
  private static final String LEGACY_TOKEN =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ3b3Jrc3BhY2VfaWQiOiIwMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0w"
          + "MDAwMDAwMDAwMTIiLCJwcm92aWRlciI6IkdJVEhVQiIsImlzcyI6Im1vbWVucy1hcGkiLCJzdWIiOiIwMDAwMDAw"
          + "MC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDEiLCJhdWQiOlsic291cmNlLW9hdXRoIl0sImV4cCI6MTc4NzI3"
          + "MTAwMCwiaWF0IjoxNzg3MjcwNDAwLCJqdGkiOiJvM3dJellWaWg0ajdNazN3eU8wXy13In0.ENJ4kZCX06qolJMk"
          + "PSvngatJgAOMtKJsjZWS5JJXJnw";

  private static OAuthStateSigner signerAt(Instant now) {
    return new OAuthStateSigner(SECRET, Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("레거시에서 서명한 state를 검증한다")
  void verifiesStateSignedByLegacyGoSigner() {
    OAuthStateSigner signer = signerAt(LEGACY_ISSUED_AT.plusSeconds(60));

    OAuthState state = signer.verify(LEGACY_TOKEN);

    assertThat(state).isEqualTo(new OAuthState(WORKSPACE_ID, USER_ID, "GITHUB"));
  }

  @Test
  @DisplayName("만료 시각이 지난 레거시 state는 거부한다")
  void rejectsLegacyStateAfterItExpires() {
    OAuthStateSigner signer = signerAt(LEGACY_ISSUED_AT.plusSeconds(601));

    assertThat(signer.verify(LEGACY_TOKEN)).isNull();
  }

  @Test
  @DisplayName("다른 비밀로 서명한 레거시 state는 거부한다")
  void rejectsLegacyStateSignedWithAnotherSecret() {
    OAuthStateSigner other =
        new OAuthStateSigner(
            "another-secret-value",
            Duration.ofMinutes(10),
            Clock.fixed(LEGACY_ISSUED_AT.plusSeconds(60), ZoneOffset.UTC));

    assertThat(other.verify(LEGACY_TOKEN)).isNull();
  }

  @Test
  @DisplayName("서명한 state를 다시 검증하면 원래 내용을 반환한다")
  void roundTripsThroughSignAndVerify() {
    OAuthStateSigner signer = signerAt(LEGACY_ISSUED_AT);
    OAuthState state = new OAuthState(WORKSPACE_ID, USER_ID, "SLACK");

    assertThat(signer.verify(signer.sign(state))).isEqualTo(state);
  }

  @Test
  @DisplayName("JWT 형식이 아닌 문자열은 거부한다")
  void rejectsTokenThatIsNotAJwt() {
    assertThat(signerAt(LEGACY_ISSUED_AT).verify("not-a-token")).isNull();
  }

  @Test
  @DisplayName("provider 이름은 앞뒤 공백을 제거하고 대문자로 정규화한다")
  void normalizesProviderToUpperCaseWithoutSurroundingSpaces() {
    assertThat(OAuthStateSigner.normalize("  github ")).isEqualTo("GITHUB");
    assertThat(OAuthStateSigner.normalize(null)).isEmpty();
  }
}
