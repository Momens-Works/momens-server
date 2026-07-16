package works.momens.server.notification.internal;

import java.util.Map;

/**
 * push 한 건의 표시 문구와 FCM data payload(docs/design/signal-push-demo-design.md 7절).
 *
 * <p>background/종료 상태의 시스템 표시를 위한 notification payload(title/body)와 클릭 라우팅용 data payload를 함께 보낸다.
 */
record PushMessage(String title, String body, Map<String, String> data) {}
