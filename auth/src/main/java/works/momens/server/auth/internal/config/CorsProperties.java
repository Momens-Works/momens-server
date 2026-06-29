package works.momens.server.auth.internal.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CORS 설정(`momens.cors.*`).
 *
 * <p>새 서버에 브라우저로 붙는 건 웹 프론트(momens-fe)뿐입니다. 웹 앱 origin과 API origin이 달라 브라우저가 쿠키를 실은 요청을 보내려면 서버가
 * CORS 헤더를 내려줘야 합니다. 모바일은 Authorization 헤더로 호출하는 네이티브 클라이언트라 브라우저 origin이 없어 CORS 대상이 아닙니다.
 *
 * <p>웹 HttpOnly 쿠키 인증을 cross-origin에서 살리려면 {@code allowCredentials=true}가 필요하고, 그때는 와일드카드 origin을 쓸
 * 수 없어 {@code allowedOrigins}로 허용 origin을 명시합니다. 운영에서는 {@code MOMENS_CORS_ALLOWED_ORIGINS} 환경 변수로
 * 주입합니다.
 */
@Validated
@ConfigurationProperties("momens.cors")
public record CorsProperties(
    List<String> allowedOrigins,
    List<String> allowedMethods,
    List<String> allowedHeaders,
    Boolean allowCredentials,
    Duration maxAge) {

  private static final List<String> DEFAULT_ALLOWED_METHODS =
      List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
  private static final List<String> DEFAULT_ALLOWED_HEADERS =
      List.of("Content-Type", "Authorization", "API-Version");
  private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    allowedMethods =
        (allowedMethods == null || allowedMethods.isEmpty())
            ? DEFAULT_ALLOWED_METHODS
            : List.copyOf(allowedMethods);
    allowedHeaders =
        (allowedHeaders == null || allowedHeaders.isEmpty())
            ? DEFAULT_ALLOWED_HEADERS
            : List.copyOf(allowedHeaders);
    if (allowCredentials == null) {
      allowCredentials = Boolean.TRUE;
    }
    if (maxAge == null) {
      maxAge = DEFAULT_MAX_AGE;
    }
  }
}
