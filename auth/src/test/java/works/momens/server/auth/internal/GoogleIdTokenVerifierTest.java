package works.momens.server.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.common.api.BusinessException;

class GoogleIdTokenVerifierTest {

  private static final String AUDIENCE = "test-google-client-id.apps.googleusercontent.com";
  private static final String SECRET = "unit-test-momens-auth-jwt-secret-0123456789abcdef";

  @Test
  void verifiesGoogleIdTokenAndExtractsUserInfo() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    try (JwksServer jwks = JwksServer.start(key)) {
      GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(properties(jwks.jwkSetUri()));

      GoogleUserInfo user = verifier.verify(idToken(key, AUDIENCE, true));

      assertThat(user.email()).isEqualTo("user@example.com");
      assertThat(user.name()).isEqualTo("규일");
      assertThat(user.picture()).isEqualTo("https://cdn.momens.works/avatar.png");
    }
  }

  @Test
  void rejectsInvalidAudience() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    try (JwksServer jwks = JwksServer.start(key)) {
      GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(properties(jwks.jwkSetUri()));

      assertThatThrownBy(() -> verifier.verify(idToken(key, "other-client-id", true)))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
    }
  }

  @Test
  void rejectsUnverifiedEmail() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    try (JwksServer jwks = JwksServer.start(key)) {
      GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(properties(jwks.jwkSetUri()));

      assertThatThrownBy(() -> verifier.verify(idToken(key, AUDIENCE, false)))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_GOOGLE_EMAIL_NOT_VERIFIED));
    }
  }

  private static AuthProperties properties(String jwkSetUri) {
    return new AuthProperties(
        SECRET,
        Duration.ofMinutes(15),
        Duration.ofDays(14),
        new AuthProperties.Google(List.of(AUDIENCE), jwkSetUri));
  }

  private static String idToken(RSAKey key, String audience, boolean emailVerified)
      throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .audience(audience)
            .subject("google-sub")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("email", "user@example.com")
            .claim("email_verified", emailVerified)
            .claim("name", "규일")
            .claim("picture", "https://cdn.momens.works/avatar.png")
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
    jwt.sign(new RSASSASigner(key.toPrivateKey()));
    return jwt.serialize();
  }

  private record JwksServer(HttpServer server, String jwkSetUri) implements AutoCloseable {

    static JwksServer start(RSAKey key) throws Exception {
      String body = new JWKSet(key.toPublicJWK()).toString();
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/jwks",
          exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
      int port = server.getAddress().getPort();
      return new JwksServer(server, "http://127.0.0.1:" + port + "/jwks");
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
