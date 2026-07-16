/**
 * Signal 목록·상세·action 위임 하위 도메인.
 *
 * <p>mobile 모듈 안에서 {@code /api/mobile/projects/{projectId}/signals}, {@code /api/mobile/signals/*}가
 * 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-0799). Signal 도메인 정책·영속성은 signal 모듈이 소유하므로, 이 패키지는
 * signal의 public API({@link works.momens.server.signal.SignalListService}, {@link
 * works.momens.server.signal.SignalDetailService}, {@link
 * works.momens.server.signal.SignalActionService})에 위임만 하는 얇은 Controller와 응답 DTO만 둡니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.mobile.signal;
