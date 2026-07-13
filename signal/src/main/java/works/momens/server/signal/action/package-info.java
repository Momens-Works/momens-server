/**
 * Signal action 하위 도메인.
 *
 * <p>signal 모듈 안에서 사용자 action(convert-to-task, dismiss) 기록과 멱등성을 담당하는 경계를 Spring Modulith nested
 * 모듈로 명시합니다(MOM-65). 외부에는 모듈 root의 {@link works.momens.server.signal.SignalActionService}로만 공개하고,
 * ledger 엔티티·리포지토리·구현체는 이 패키지 안에 은닉합니다. {@code convert-to-task}·{@code dismiss} 트랜잭션은 {@code
 * SignalActionExecutor}가 소유하며, signal aggregate 이벤트(converted_to_task·dismissed) outbox insert가 이
 * 트랜잭션에 합류합니다(MOM-0688).
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.signal.action;
