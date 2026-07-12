/**
 * Mobile 모듈 공개 API.
 *
 * <p>모듈 root package는 Spring Modulith 공개 표면이다(docs/rules/architecture.md). 조합 서비스·DTO·컨트롤러는 {@code
 * internal}/{@code presentation}에 두고, 이 패키지는 모듈 밖(테스트 포함)에서 참조해도 되는 계약만 둔다.
 *
 * <ul>
 *   <li>시간 seam — {@link works.momens.server.mobile.MobileClock}. 브리프의 "오늘"이 현재 시각에 의존하므로 통합 테스트가
 *       내부를 넘겨보지 않고 고정 Clock으로 덮어쓸 수 있게 공개한다. 배선은 {@code internal.MobileTimeConfig}가 소유한다.
 * </ul>
 */
package works.momens.server.mobile;
