package works.momens.server.project;

import java.util.List;
import java.util.UUID;

/** project 모듈의 blocker 조회 public API. */
public interface BlockerReader {

  /** 워크스페이스의 blocker를 생성 시각 내림차순으로 조회합니다. */
  List<BlockerDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
