/**
 * 태스크 하위 도메인.
 *
 * <p>project 모듈 안에서 태스크가 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-71). 외부에는 모듈 root의 {@link
 * works.momens.server.project.TaskReader}와 {@link works.momens.server.project.TaskCreator}로만 공개합니다.
 * 태스크 생성 트랜잭션에 라벨 발급이 참여하는 이유는 docs/design/module-map.md workspace 절에 있습니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.project.task;
