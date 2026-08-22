package works.momens.server.memory;

import java.util.UUID;

/**
 * {@code memory} 모듈의 확정 메모리 생성 public API입니다.
 *
 * <p>{@link MemoryWriter}에 메서드를 추가하지 않고 별도의 API로 분리합니다. 해당 인터페이스는 후보 검토(H084~H088)와 메모리 해결(H093)로
 * 책임이 한정되어 있으며, 검증 조건도 확정 메모리 생성 경로와 다릅니다. 후보가 존재하고 {@code PROPOSED} 상태인지 확인하며, 요청자가 워크스페이스 멤버인지도
 * 검증합니다.
 *
 * <p>검색 반영 이벤트는 생성하지 않습니다. 레거시는 수동 생성과 후보 확정 시에만 검색 문서를 갱신하며, 워크스페이스 생성 과정에서 저장하는 메모리에는 검색 반영 처리를
 * 수행하지 않습니다({@code workspace/seed.go:40}). 신규 서버도 같은 동작을 유지합니다.
 *
 * <p>요청자의 권한은 확인하지 않습니다. 호출하는 쪽에서 소속과 권한을 확인한 워크스페이스에 메모리를 저장합니다.
 */
public interface ConfirmedMemoryCreator {

  /** 확정 메모리 한 건을 저장하고 생성된 행의 식별자를 반환합니다. */
  UUID create(CreateConfirmedMemoryCommand command);
}
