package works.momens.server.auth.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * 우리 access token 발급.
 *
 * <p>클레임은 플랫폼 중립으로 최소화합니다(sub=userId, iat, exp). 전송수단(Bearer/쿠키)·role/권한 정보는 토큰에 넣지 않습니다.
 * principal은 sub=userId만 신뢰하며, 권한 판단은 추후 workspace public API에서 DB 기준으로 합니다.
 */
@Service
public class JwtTokenService {

  private final JwtEncoder accessTokenEncoder;
  private final AuthProperties properties;
  private final Clock clock;

  JwtTokenService(JwtEncoder accessTokenEncoder, AuthProperties properties, Clock clock) {
    this.accessTokenEncoder = accessTokenEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  /** userId를 sub로 담은 HS256 access token을 발급합니다. */
  public String issueAccessToken(UUID userId) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTtl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return accessTokenEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
