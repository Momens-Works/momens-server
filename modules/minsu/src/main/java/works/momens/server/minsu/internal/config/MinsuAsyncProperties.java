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
 * <p><b>provider가 활성이지만 설정이 무효한 경우는 설계가 아직 갈린다.</b> 9.2절은 적재한 뒤 claim해서 {@code invalid_config}로
 * 닫는다고 하고(설정 오류가 원장과 지표에 드러나야 한다는 이유로 {@code disabled}와의 비대칭을 의도했다고 명시), 11.2절은 claim하지 않고 {@code
 * pending}으로 두며 사유를 기록하지 않는다고 한다. 여기서 임의로 정하지 않는다. 적재·claim 조건을 실제로 구현하는 MOM-0818·0819 전에 설계 문서에서
 * 하나로 확정한다.
 */
@Validated
@ConfigurationProperties("momens.minsu.task-draft.async")
public record MinsuAsyncProperties(
    @DefaultValue("false") boolean enroll, @DefaultValue("false") boolean drain) {}
