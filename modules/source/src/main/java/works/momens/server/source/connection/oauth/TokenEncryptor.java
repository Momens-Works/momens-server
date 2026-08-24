package works.momens.server.source.connection.oauth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * provider 접근 토큰을 저장하기 위한 암호화 기능을 제공합니다.
 *
 * <p>AES-256-GCM으로 암호화하고, 12바이트 nonce를 암호문 앞에 붙인 바이트 배열을 {@code BYTEA} 컬럼에 저장합니다. 추가 인증 데이터는 사용하지
 * 않으며 형식을 나타내는 접두사도 추가하지 않습니다.
 *
 * <p><strong>이 저장 형식은 신규 서버만의 기준으로 변경할 수 없습니다.</strong> {@code momens-worker}가 같은 키로 값을 복호화해 외부
 * source를 수집하고, 레거시 {@code momens-api}도 같은 형식으로 토큰을 저장합니다. 세 서버가 같은 테이블을 공유하므로 바이트 배치가 하나라도 다르면 다른
 * 서버에서 생성한 값을 복호화할 수 없습니다.
 *
 * <p>호환성은 실제 실행을 통해 양방향으로 확인했습니다. 레거시에서 암호화한 값을 이 클래스에서 복호화하고, 이 클래스에서 암호화한 값을 레거시에서 복호화했습니다. 레거시가
 * 생성한 실제 바이트 배열도 {@code TokenEncryptorTest}에 고정해 회귀 테스트로 검증합니다.
 *
 * <p>키는 base64로 인코딩된 32바이트 값이어야 합니다. 조건을 충족하지 않으면 객체 생성 시점에 실패시켜 잘못된 키로 암호화한 값이 저장되지 않도록 합니다.
 */
class TokenEncryptor {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String ALGORITHM = "AES";
  private static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  TokenEncryptor(String base64Key) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("token key must be base64", e);
    }
    if (decoded.length != KEY_BYTES) {
      throw new IllegalArgumentException("token key must decode to 32 bytes");
    }
    this.key = new SecretKeySpec(decoded, ALGORITHM);
  }

  private TokenEncryptor() {
    this.key = null;
  }

  static TokenEncryptor unavailable() {
    return new TokenEncryptor();
  }

  /**
   * 평문이 비어 있으면 {@code null}을 반환합니다.
   *
   * <p>레거시와 동일한 동작이며, 반환값은 nullable 컬럼에 저장됩니다.
   */
  byte[] encrypt(String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      return null;
    }
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    byte[] sealed = run(Cipher.ENCRYPT_MODE, nonce, plaintext.getBytes(StandardCharsets.UTF_8));
    byte[] out = new byte[nonce.length + sealed.length];
    System.arraycopy(nonce, 0, out, 0, nonce.length);
    System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
    return out;
  }

  /**
   * 값이 비어 있으면 빈 문자열을 반환합니다.
   *
   * <p>저장된 값이 nonce 길이보다 짧으면 유효하지 않은 암호문으로 판정해 실패시킵니다.
   */
  String decrypt(byte[] payload) {
    if (payload == null || payload.length == 0) {
      return "";
    }
    if (payload.length <= NONCE_BYTES) {
      throw new IllegalArgumentException("encrypted payload is too short");
    }
    byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
    byte[] sealed = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
    return new String(run(Cipher.DECRYPT_MODE, nonce, sealed), StandardCharsets.UTF_8);
  }

  private byte[] run(int mode, byte[] nonce, byte[] input) {
    if (key == null) {
      throw new IllegalStateException("source token key is not configured");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("source token cipher failed", e);
    }
  }
}
