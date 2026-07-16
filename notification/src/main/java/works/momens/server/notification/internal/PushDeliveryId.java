package works.momens.server.notification.internal;

import java.io.Serializable;
import java.util.UUID;

/** {@link PushDelivery} 복합 PK. */
record PushDeliveryId(long outboxEventId, UUID installationId) implements Serializable {}
