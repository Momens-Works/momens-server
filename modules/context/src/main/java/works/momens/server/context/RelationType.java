package works.momens.server.context;

/**
 * {@code entity_relations}의 {@code relation_type}에 저장하는 값입니다.
 *
 * <p>{@code LINKED_TO}는 태스크에 메모리나 source-ref를 연결한 관계이며, {@code RESOLVES}는 한 메모리가 다른 메모리를 해결한 관계입니다.
 * 정의 범위와 확장 기준은 {@link EntityType}과 같습니다.
 */
public enum RelationType {
  LINKED_TO,
  RESOLVES
}
