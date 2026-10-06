package works.momens.server.source.connection;

/** source_connections.status의 DB CHECK 제약과 같은 상태 집합입니다. */
public enum SourceConnectionStatus {
  PENDING,
  ACTIVE,
  DISABLED,
  ERROR,
  REVOKED
}
