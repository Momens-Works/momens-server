package works.momens.server.auth.internal.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;

/**
 * dev 도구 전용 빈에 다는 프로필 게이트. {@code local}, {@code dev}, {@code test}에서만 등록되고 {@code prod}와 프로필 미설정
 * 환경에서는 등록되지 않습니다.
 *
 * <p>허용 프로필을 명시하는 allowlist로 둡니다. {@code @Profile("!prod")}는 denylist라 프로필이 설정되지 않은 배포(예: {@code
 * SPRING_PROFILES_ACTIVE} 누락)에서도 열려 dev 전용 도구가 노출될 수 있습니다. allowlist는 나열한 프로필에서만 열리고 그 외에는 닫혀
 * fail-closed입니다(OWASP Secure by Default).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile({"local", "dev", "test"})
public @interface DevOnly {}
