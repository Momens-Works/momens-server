/**
 * 멤버십과 RBAC 하위 도메인.
 *
 * <p>workspace 모듈 안에서 멤버십이 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-70). 이 하위 도메인은 모듈 루트의
 * {@code WorkspaceMembershipReader}와 {@code WorkspaceMembershipWriter}를 통해서만 외부에 공개됩니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.workspace.access;
