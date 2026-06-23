/**
 * 공유 영속성 베이스 모듈.
 *
 * <p>여러 기능 모듈이 의존하는 기술 인프라이므로 OPEN 모듈로 둡니다. 그래야 하위 package(예: {@code persistence})의 {@code
 * BaseEntity}를 다른 모듈이 직접 참조해도 Spring Modulith 경계 검증을 통과합니다.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package works.momens.server.common;

import org.springframework.modulith.ApplicationModule;
