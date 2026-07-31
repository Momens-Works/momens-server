/**
 * 멤버십과 RBAC 하위 도메인.
 *
 * <p>workspace 모듈 안에서 멤버십이 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-70). 외부에는 모듈 root의 {@link
 * works.momens.server.workspace.WorkspaceAccess}로만 공개합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.workspace.access;
