/**
 * 웹 메모리 하위 도메인입니다.
 *
 * <p>레거시 웹 클라이언트의 메모리 후보 리뷰({@code POST /api/memory-candidates/{candidateId}/...})와 메모리 해결({@code
 * POST /api/memories/{memoryId}/resolve})이 담당하는 경계를 Spring Modulith의 nested 모듈로 명시합니다. {@code
 * memory} 모듈의 public API를 호출할 뿐 리뷰 정책을 직접 소유하지 않으므로 외부에 공개하는 계약은 없습니다. Controller, 조합 서비스, DTO는 이
 * 패키지에서 함께 관리합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.web.memory;
