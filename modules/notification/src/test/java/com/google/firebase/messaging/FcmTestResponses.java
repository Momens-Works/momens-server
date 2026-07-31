package com.google.firebase.messaging;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;

/**
 * {@link SendResponse}와 {@link FirebaseMessagingException}은 final class에 package-private 팩토리만 있어
 * mock할 수 없습니다. 같은 패키지에서 실제 인스턴스를 만들어 FCM adapter 테스트에 제공합니다.
 */
public final class FcmTestResponses {

  private FcmTestResponses() {}

  public static SendResponse success() {
    return SendResponse.fromMessageId("projects/test/messages/1");
  }

  public static SendResponse failure(MessagingErrorCode errorCode) {
    return SendResponse.fromException(messagingException(errorCode));
  }

  public static FirebaseMessagingException messagingException(MessagingErrorCode errorCode) {
    return FirebaseMessagingException.withMessagingErrorCode(
        new FirebaseException(ErrorCode.UNAVAILABLE, "test", null), errorCode);
  }
}
