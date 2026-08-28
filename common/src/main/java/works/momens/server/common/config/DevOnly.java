package works.momens.server.common.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;

/**
 * dev 도구 전용 빈에 다는 프로필 게이트. {@code local}, {@code dev}, {@code test} 중 하나가 활성이고 {@code prod}가 활성이 아닐
 * 때만 등록됩니다.
 *
 * <p>{@code prod}를 식에서 명시적으로 제외합니다. {@code @Profile}의 배열({@code {"local","dev","test"}})은 OR라,
 * Spring이 여러 프로필 동시 활성을 지원하므로 {@code prod,dev} 같은 조합에서도 dev가 들어 있어 등록됩니다. {@code !prod & (local |
 * dev | test)}는 prod가 함께 활성이면 무조건 제외하고, 프로필 미설정 환경에서도 닫혀 fail-closed입니다(OWASP Secure by Default).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile("!prod & (local | dev | test)")
public @interface DevOnly {}
