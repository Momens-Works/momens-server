package works.momens.server.context;

/**
 * {@code entity_relations}의 {@code from_entity_type}과 {@code to_entity_type}에 저장하는 값입니다.
 *
 * <p>이름은 레거시 {@code momens-api}의 도메인 상수 및 {@code momens-proto}의 {@code
 * momens.entity.v1.EntityType}에서 접두사를 제거한 값과 같습니다. 해당 서버에서 연결하는 엔티티 종류만 정의하며, 나머지는 필요해질 때 추가합니다.
 *
 * <p>엔티티 필드는 문자열로 유지하고 해당 타입은 쓰기 명령에만 사용합니다. 같은 테이블을 worker와 레거시 서버도 사용하므로 정의되지 않은 값이 저장된 행을 읽더라도
 * 조회가 실패해서는 안 됩니다.
 */
public enum EntityType {
  TASK,
  MEMORY,
  SOURCE_OBJECT
}
