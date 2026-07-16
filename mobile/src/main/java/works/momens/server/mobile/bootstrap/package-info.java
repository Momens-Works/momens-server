/**
 * 모바일 부트스트랩 화면 하위 도메인.
 *
 * <p>mobile 모듈 안에서 {@code GET /api/mobile/bootstrap}이 담당하는 경계를 Spring Modulith nested 모듈로
 * 명시합니다(MOM-0799). 다른 nested 모듈과 마찬가지로 aggregate가 아니라 화면 단위 조합 슬라이스라, 외부에 공개하는 계약이 없고 Controller·조합
 * 서비스·DTO를 이 패키지 하나에 함께 둡니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.mobile.bootstrap;
