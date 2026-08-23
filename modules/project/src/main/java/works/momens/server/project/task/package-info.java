/**
 * 태스크 하위 도메인.
 *
 * <p>project 모듈 안에서 태스크가 담당하는 공개 경계를 Spring Modulith named interface로 명시합니다(MOM-71). 외부에는 task
 * root의 {@link works.momens.server.project.task.TaskReader}와 {@link
 * works.momens.server.project.task.TaskCreator}로만 공개합니다. 태스크 생성 트랜잭션에 라벨 발급이 참여하는 이유는
 * docs/design/module-map.md workspace 절에 있습니다.
 */
@org.springframework.modulith.NamedInterface
package works.momens.server.project.task;
