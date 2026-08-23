package works.momens.server.project.core;

import java.util.List;
import java.util.UUID;

/** project core가 소유하는 프로젝트 소유자 조회 계약. */
public interface ProjectOwnerReader {

  /** 프로젝트 소유자를 생성 시각과 사용자 식별자 오름차순으로 조회합니다. */
  List<UUID> listOwnerUserIds(UUID projectId);
}
