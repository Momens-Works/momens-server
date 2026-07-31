/**
 * 프로젝트 멤버 조회(담당자 후보 명단) 화면 하위 도메인.
 *
 * <p>mobile 모듈 안에서 {@code GET /api/mobile/projects/{projectId}/members}가 담당하는 경계를 Spring Modulith
 * nested 모듈로 명시합니다(MOM-0799). project·workspace·user의 public API를 조합할 뿐 멤버십 자체를 소유하지 않으므로,
 * workspace의 멤버십 개념과 헷갈리지 않도록 {@code members}가 아닌 {@code roster}로 이름 붙였습니다. 외부에 공개하는 계약이 없고
 * Controller·조합 서비스·DTO를 이 패키지 하나에 함께 둡니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.mobile.roster;
