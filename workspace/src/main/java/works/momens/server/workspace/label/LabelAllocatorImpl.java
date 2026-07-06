package works.momens.server.workspace.label;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.LabelAllocator;

@Service
@RequiredArgsConstructor
class LabelAllocatorImpl implements LabelAllocator {

  private static final String MOM_PREFIX = "MOM";

  private final WorkspaceLabelSequenceRepository sequenceRepository;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public String allocateMomLabel(UUID workspaceId) {
    // 이 메서드는 자기를 부른 쪽의 트랜잭션 안에서만 동작한다(MANDATORY). 라벨은 task 같은 대상을
    // 만드는 작업의 일부라서, 그 작업이 중간에 실패해 되돌려지면 발급한 번호도 같이 되돌아가야 한다.
    // 부른 쪽과 같은 트랜잭션으로 묶이니 이 되돌림이 보장된다. 트랜잭션 없이 혼자 부르면 예외로 막아
    // 잘못된 사용을 일찍 잡는다. 같은 번호가 두 번 나가지 않는 건 repository의 ON CONFLICT 한 문장이
    // 책임진다.
    long allocated = sequenceRepository.allocate(workspaceId, MOM_PREFIX);
    return String.format("%s-%04d", MOM_PREFIX, allocated);
  }
}
