package works.momens.server.source.internal.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레거시와 토큰 암호화 저장 형식이 호환되는지 검증합니다.
 *
 * <p>첫 번째 테스트의 입력값은 레거시 코드를 실제로 실행해 얻은 바이트 배열입니다. 저장 형식이 달라지면 해당 테스트가 먼저 실패합니다.
 */
class TokenEncryptorTest {

  private static final String KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  private final TokenEncryptor cipher = new TokenEncryptor(KEY);

  @Test
  @DisplayName("레거시에서 암호화한 값을 복호화한다")
  void decryptsPayloadSealedByLegacyGoCipher() {
    byte[] payload =
        Base64.getDecoder()
            .decode("Rwksk/fjQqD1206dvWK6r9cRs0/fcB7V9FUkZE+besWHX3ntF439dIoQ/3JhBVn2urw=");

    assertThat(cipher.decrypt(payload)).isEqualTo("gho_exampleaccesstoken");
  }

  @Test
  @DisplayName("암호화 결과에 12바이트 nonce와 16바이트 인증 태그가 포함된다")
  void encryptsWithTwelveByteNonceAndSixteenByteTag() {
    String plaintext = "gho_exampleaccesstoken";

    byte[] payload = cipher.encrypt(plaintext);

    assertThat(payload).hasSize(12 + plaintext.length() + 16);
  }

  @Test
  @DisplayName("암호화한 값을 다시 복호화하면 원래 문자열을 반환한다")
  void roundTripsThroughEncryptAndDecrypt() {
    byte[] payload = cipher.encrypt("xoxb-slack-bot-token");

    assertThat(cipher.decrypt(payload)).isEqualTo("xoxb-slack-bot-token");
  }

  @Test
  @DisplayName("같은 문자열을 암호화해도 매번 다른 값을 생성한다")
  void producesDifferentPayloadsForTheSamePlaintext() {
    byte[] first = cipher.encrypt("same-token");
    byte[] second = cipher.encrypt("same-token");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("빈 문자열은 저장하지 않고 빈 암호문은 빈 문자열로 복호화한다")
  void returnsNullForEmptyPlaintextAndEmptyStringForEmptyPayload() {
    assertThat(cipher.encrypt("")).isNull();
    assertThat(cipher.decrypt(new byte[0])).isEmpty();
  }

  @Test
  @DisplayName("32바이트가 아닌 키는 생성 시점에 거부한다")
  void rejectsKeyThatIsNotThirtyTwoBytes() {
    String shortKey =
        Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> new TokenEncryptor(shortKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  @DisplayName("nonce보다 짧은 값은 복호화하지 않는다")
  void rejectsPayloadShorterThanNonce() {
    assertThatThrownBy(() -> cipher.decrypt(new byte[8]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
