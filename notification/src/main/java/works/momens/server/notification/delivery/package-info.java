/**
 * signal.created 소비·발송 하위 도메인.
 *
 * <p>notification 모듈 안에서 outbox 소비(watermark 관리)와 기기별 발송 상태 — materialization, 클레임, 백오프 재시도, 실패 격리
 * — 가 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(aggregate: {@code PushDelivery}·{@code
 * NotificationConsumerOffset}). 설치 원장은 device의 {@link
 * works.momens.server.notification.device.PushInstallationDirectory}로만 읽고, FCM 전송은 fcm의 {@link
 * works.momens.server.notification.fcm.FcmClient} 경계 뒤에서 수행합니다. 다른 모듈에 공개할 계약이 없어 전부 이 패키지에 은닉합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.notification.delivery;
