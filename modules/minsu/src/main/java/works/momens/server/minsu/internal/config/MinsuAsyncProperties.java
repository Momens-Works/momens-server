package works.momens.server.minsu.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 비동기 생성의 설정 축(docs/design/minsu-async-task-draft-design.md 11.2절).
 *
 * <p>축은 셋이고 provider 축은 기존 {@link MinsuTaskDraftProperties#enabled()}가 그대로 담당한다(모델 호출 활성 여부). 여기서는
 * 비동기 도입으로 새로 생기는 두 축만 갖는다.
 *
 * <ul>
 *   <li>{@code enroll} — 신규 convert가 원장을 적재하는가. 끄면 신규는 동기 경로로 가고 기존 원장은 그대로 처리된다.
 *   <li>{@code drain} — scheduler가 원장을 처리하는가. 끄면 기존 원장이 멈추고 {@code read_deadline_at}이 지나면 읽기 투영이
 *       {@code ready}로 닫는다.
 * </ul>
 *
 * <p>축 사이에는 파생 규칙이 하나 있다. <b>provider가 비활성이면 {@code enroll} 값과 무관하게 적재하지 않는다</b>(11.2절 표, 9.2절
 * {@code disabled} 행). 운영자가 두 값을 맞춰 끄는 것이 아니라 적재 여부를 {@code provider.enabled && enroll}로 판정해 코드가
 * 강제한다. 이 판정을 쓰는 코드는 MOM-0818(적재)과 MOM-0819(drain)에 붙는다.
 *
 * <p>provider가 활성이지만 <b>설정이 무효한 경우는 적재는 하되 claim하지 않는다</b>(11.2절). 설정을 고치면 그대로 이어서 처리할 수 있는 작업이므로
 * 종료로 기록하지 않고, 그 전에 {@code read_deadline_at}이 지나면 읽기 투영이 닫는다. 따라서 설정 오류의 1차 관측은 원장이 아니라 {@code
 * momens.minsu.llm.config.valid} gauge다({@link MinsuConfigStatus}). 종료 사유 {@code invalid_config}는
 * claim 이후 설정이 무효해진 경합에서만 도달한다.
 */
@Validated
@ConfigurationProperties("momens.minsu.task-draft.async")
public record MinsuAsyncProperties(
    @DefaultValue("false") boolean enroll, @DefaultValue("false") boolean drain) {}
