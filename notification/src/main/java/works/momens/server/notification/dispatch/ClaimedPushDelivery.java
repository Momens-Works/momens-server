package works.momens.server.notification.dispatch;

import java.util.UUID;

/** 클레임된 delivery 한 건. 클레임 시점 installation의 현재 token을 함께 든다(전송 트랜잭션 밖에서 사용). */
record ClaimedPushDelivery(long outboxEventId, UUID installationId, String fcmRegistrationToken) {}
