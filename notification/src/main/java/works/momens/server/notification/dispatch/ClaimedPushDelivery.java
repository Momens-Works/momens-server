package works.momens.server.notification.dispatch;

import java.util.UUID;

/** 클레임된 delivery 한 건. 클레임 시점 installation의 현재 token과 오래된 결과를 거르는 claim token을 함께 든다. */
record ClaimedPushDelivery(
    long outboxEventId, UUID installationId, String fcmRegistrationToken, UUID claimToken) {}
