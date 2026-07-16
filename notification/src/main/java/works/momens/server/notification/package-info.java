/**
 * Notification 모듈 공개 API.
 *
 * <p>모듈 root package는 Spring Modulith 공개 표면이다(docs/rules/architecture.md). Signal 발생 push
 * notification의 소비·발송과 push 설치(FID/FCM token) lifecycle을 이 모듈이 소유한다
 * (docs/design/signal-push-demo-design.md, ADR-0009). 엔티티·리포지토리·구현체는 {@code internal}에 은닉한다.
 *
 * <ul>
 *   <li>설치 등록·해제 API — {@link works.momens.server.notification.PushDeviceRegistrar}. HTTP 표면({@code
 *       /api/me/push-devices/*})은 mobile 모듈이 소유하고 이 API에 위임한다.
 * </ul>
 *
 * <p>{@code signal.created} consumer(watermark 관리 포함)와 FCM 발송기는 다른 모듈에 공개할 계약이 없어 {@code
 * internal}에만 있다. outbox 조회, Signal·Project hydrate, 수신자 결정은 각 모듈의 public API를 사용한다(11.1절 의존 방향).
 */
package works.momens.server.notification;
