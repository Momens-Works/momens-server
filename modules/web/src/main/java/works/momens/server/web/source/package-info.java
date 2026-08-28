/**
 * 웹 소스 하위 도메인입니다.
 *
 * <p>레거시 웹 클라이언트의 소스 연동 조회·설치({@code /api/workspaces/{workspaceId}/source-connections})와 source-ref
 * 검증({@code POST /api/source-refs/{sourceRefId}/verify})이 담당하는 경계를 Spring Modulith의 nested 모듈로
 * 명시합니다. {@code source}와 {@code workspace} 모듈의 public API만 사용하며 연동 정책은 소유하지 않으므로 외부에 공개하는 계약은 없습니다.
 * Controller, 조합 서비스, DTO는 이 패키지에서 함께 관리합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.web.source;
