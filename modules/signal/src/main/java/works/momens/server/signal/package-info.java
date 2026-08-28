/**
 * Signal 모듈 공개 API.
 *
 * <p>모듈 root package는 Spring Modulith 공개 표면이다(docs/rules/architecture.md). 구현체·엔티티·리포지토리는 {@code
 * query}/{@code action} nested 모듈에 package-private로 은닉하고, 이 패키지는 계약(인터페이스·DTO)만 둔다.
 *
 * <ul>
 *   <li>조회 API — {@link works.momens.server.signal.SignalListService}, {@link
 *       works.momens.server.signal.SignalDetailService}, {@link
 *       works.momens.server.signal.SignalReader}(action 전용 최소 read 경계)와 {@link
 *       works.momens.server.signal.SignalDetail}, {@link works.momens.server.signal.SignalSummary}.
 *   <li>action API — {@link works.momens.server.signal.SignalActionService}와 {@link
 *       works.momens.server.signal.SignalActionResult}.
 * </ul>
 */
package works.momens.server.signal;
