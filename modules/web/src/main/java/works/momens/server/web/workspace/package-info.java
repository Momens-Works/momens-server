/**
 * 웹 워크스페이스 조회 화면 하위 도메인.
 *
 * <p>web 모듈 안에서 {@code GET /api/workspaces}, {@code GET /api/workspaces/{workspaceId}}가 담당하는 경계를
 * Spring Modulith nested 모듈로 명시합니다(mobile 모듈의 MOM-0799 전례). workspace의 public API만 조합할 뿐 도메인 정책을
 * 소유하지 않으므로, 외부에 공개하는 계약이 없고 Controller·조합 서비스·DTO를 이 패키지 하나에 함께 둡니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.web.workspace;
