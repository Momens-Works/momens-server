/**
 * push 발송 하위 도메인.
 *
 * <p>notification 모듈 안에서 기기별 발송 상태와 배달 실행 — pending 기록, {@code FOR UPDATE SKIP LOCKED} 클레임, 백오프
 * 재시도, 실패 격리, event 단위 hydrate와 FCM 호출 — 이 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(aggregate:
 * {@code PushDelivery}). consume에는 {@link
 * works.momens.server.notification.dispatch.PushDispatcher}(enqueue·runSendPass)만 열고, 설치 원장은
 * device의 {@link works.momens.server.notification.device.PushInstallationDirectory}로 읽으며, FCM 전송은
 * fcm의 {@link works.momens.server.notification.fcm.FcmClient} 경계 뒤에서 수행합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.notification.dispatch;
