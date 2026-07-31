/**
 * signal.created 소비 하위 도메인.
 *
 * <p>notification 모듈 안에서 outbox 폴링과 소비 진행 위치(watermark) — 안전 지연 필터, 최초 시드, 수신자 결정 — 가 담당하는 경계를
 * Spring Modulith nested 모듈로 명시합니다(aggregate: {@code NotificationConsumerOffset}). "무엇까지 소비했나"가 이
 * 경계의 변경 이유이고, "어떻게 배달하나"(재시도·클레임·FCM)는 dispatch의 {@link
 * works.momens.server.notification.dispatch.PushDispatcher} 계약 뒤에 둡니다. 수신 설치는 device의 {@link
 * works.momens.server.notification.device.PushInstallationDirectory}로 읽습니다. 폴링 스케줄러도 소비 주기의 소유자라 이
 * 패키지에 둡니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.notification.consume;
