/**
 * 워크스페이스 초대 하위 도메인입니다.
 *
 * <p>{@code workspace} 모듈에서 초대 기능이 담당하는 경계를 Spring Modulith의 nested 모듈로 명시합니다. 외부에는 모듈 루트의 {@link
 * works.momens.server.workspace.WorkspaceInvitationReader}, {@link
 * works.momens.server.workspace.WorkspaceInvitationWriter}, {@link
 * works.momens.server.workspace.WorkspaceInvitationAcceptor}만 공개합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.workspace.invitation;
