package works.momens.server.notification.internal;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * push 비활성 환경({@code momens.notification.push.enabled=false}, local·test 기본)의 배선용 구현.
 *
 * <p>비활성 환경에서는 폴러·발송기 스케줄링도 함께 꺼져 이 빈이 호출될 일이 없다. 호출되면 배선 오류이므로 조용히 삼키지 않고 바로 실패시킨다.
 */
@Component
@ConditionalOnProperty(
    name = "momens.notification.push.enabled",
    havingValue = "false",
    matchIfMissing = true)
class DisabledFcmClient implements FcmClient {

  @Override
  public List<FcmSendResult> send(List<String> tokens, PushMessage message) {
    throw new IllegalStateException("push가 비활성화된 환경에서 FCM 전송이 호출됐습니다");
  }
}
