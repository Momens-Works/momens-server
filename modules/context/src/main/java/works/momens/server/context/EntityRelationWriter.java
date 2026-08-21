package works.momens.server.context;

import java.util.UUID;

/** entity_relations를 변경하는 context capability의 public API입니다. */
public interface EntityRelationWriter {

  /** 활성 연결을 생성합니다. 호출자의 트랜잭션에 참여합니다. */
  UUID create(EntityRelationCommand command);
}
