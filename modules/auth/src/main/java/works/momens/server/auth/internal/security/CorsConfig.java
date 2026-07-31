package works.momens.server.auth.internal.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import works.momens.server.auth.internal.config.CorsProperties;

/**
 * CORS 정책 빈. {@link SecurityConfig}의 두 체인이 이 {@link CorsConfigurationSource}를 {@code .cors()}에 물려
 * 씁니다.
 *
 * <p>허용 origin을 명시 등록하고 credentials를 허용해, 웹이 HttpOnly 쿠키를 실은 cross-origin 요청을 보낼 수 있게 합니다. 와일드카드
 * origin은 credentials와 함께 쓸 수 없어 사용하지 않습니다.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.allowedOrigins());
    config.setAllowedMethods(properties.allowedMethods());
    config.setAllowedHeaders(properties.allowedHeaders());
    config.setAllowCredentials(properties.allowCredentials());
    config.setMaxAge(properties.maxAge());

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
