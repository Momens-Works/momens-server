/**
 * FCM 전송 adapter 하위 도메인.
 *
 * <p>notification 모듈 안에서 외부 Firebase 경계 — Admin SDK 초기화(ADC), multicast 분할, token별 결과 분류 — 를 Spring
 * Modulith nested 모듈로 격리합니다. delivery에는 {@link works.momens.server.notification.fcm.FcmClient}와
 * {@link works.momens.server.notification.fcm.PushMessage} 계약만 열고, Firebase SDK 타입은 이 패키지 밖으로 새지
 * 않습니다. push 비활성 환경(local·test 기본)에서는 배선용 구현이 대신 등록됩니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.notification.fcm;
