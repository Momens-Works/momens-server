package works.momens.server.signal;

import java.util.UUID;

/**
 * Signal action 처리 결과.
 *
 * <p>{@code created}는 이번 요청으로 새로 처리됐는지(false면 기존 결과를 그대로 반환한 멱등 replay)를 나타낸다. {@code task}는
 * dismiss일 때 항상 {@code null}이다.
 */
public record SignalActionResult(
    UUID signalId, String actionType, boolean created, TaskResult task) {

  public record TaskResult(UUID id, String title, String status) {}
}
