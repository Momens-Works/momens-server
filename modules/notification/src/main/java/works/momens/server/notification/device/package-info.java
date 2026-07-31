/**
 * push 설치 원장 하위 도메인.
 *
 * <p>notification 모듈 안에서 설치(FID/FCM token) lifecycle — 등록·token 갱신·소유권 이전·해제·무효 token 비활성화 — 가 담당하는
 * 경계를 Spring Modulith nested 모듈로 명시합니다(aggregate: {@code PushInstallation}). 모듈 밖 공개 계약은 root의
 * {@link works.momens.server.notification.PushDeviceRegistrar}이고, delivery nested 모듈에는 발송에 필요한 최소
 * 조회·비활성화만 {@link works.momens.server.notification.device.PushInstallationDirectory}로 엽니다.
 * 엔티티·리포지토리는 이 패키지에 은닉합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.notification.device;
