/**
 * workspace 범위 라벨 발급 하위 도메인.
 *
 * <p>workspace 모듈 안에서 라벨 발급 카운터가 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-70). 외부에는 모듈 root의
 * {@link works.momens.server.workspace.LabelAllocator}로만 공개합니다. 발급이 호출자 트랜잭션에 참여하는 이유는
 * docs/design/module-map.md workspace 절에 있습니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.workspace.label;
