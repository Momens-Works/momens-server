package works.momens.server.notification.internal;

import lombok.RequiredArgsConstructor;

/** {@link PushDelivery} 상태 저장값(docs/design/signal-push-demo-design.md 10.2절). */
@RequiredArgsConstructor
enum DeliveryStatus {
  PENDING("pending"),
  SENT("sent"),
  FAILED("failed"),
  CANCELLED("cancelled");

  private final String value;

  String value() {
    return value;
  }
}
