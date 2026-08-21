/**
 * 웹의 프로젝트 및 마일스톤 생성 기능을 담당하는 하위 도메인입니다.
 *
 * <p>{@code web} 모듈에서 {@code POST /api/workspaces/{workspaceId}/projects}와 {@code POST
 * /api/projects/{projectId}/milestones}가 담당하는 경계를 Spring Modulith의 nested 모듈로 명시합니다. project와
 * workspace의 public API를 조합할 뿐 도메인 정책을 직접 소유하지 않으므로 외부에 공개하는 계약은 없습니다. Controller, 조합 서비스, DTO는 이
 * 패키지에서 함께 관리합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.web.project;
