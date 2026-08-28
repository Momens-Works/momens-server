package works.momens.server.workspace;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * workspace 도메인 에러 코드.
 *
 * <p>공통 코드를 재사용하지 않고 도메인 의미가 드러나는 코드를 모듈이 소유합니다(docs/spec/api-response-error-codes.md). 코드·status는
 * spec 코드표와 맞춥니다. {@link WorkspaceReader}는 Optional만 반환하므로, 이 코드를 던질지는 호출하는 쪽이 결정합니다.
 */
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements ErrorCode {
  WORKSPACE_NOT_FOUND(404, "워크스페이스를 찾을 수 없습니다."),
  WORKSPACE_INVALID_SLUG(400, "사용할 수 없는 slug 형식입니다."),
  WORKSPACE_RESERVED_SLUG(400, "예약어로 지정된 slug입니다."),
  WORKSPACE_SLUG_ALREADY_EXISTS(409, "이미 사용 중인 slug입니다."),
  WORKSPACE_MEMBER_NOT_FOUND(404, "워크스페이스 멤버를 찾을 수 없습니다."),
  WORKSPACE_INVALID_ROLE(400, "부여할 수 없는 역할입니다."),
  WORKSPACE_OWNER_PROTECTED(409, "owner 멤버는 변경하거나 제거할 수 없습니다."),
  WORKSPACE_SELF_REMOVAL_NOT_ALLOWED(409, "자기 자신은 제거할 수 없습니다."),
  WORKSPACE_MEMBER_ALREADY_EXISTS(409, "이미 워크스페이스에 참여 중인 멤버입니다."),
  WORKSPACE_INVITEE_NOT_FOUND(403, "초대할 사용자를 찾을 수 없습니다."),
  WORKSPACE_MEMBER_ROLE_CONFLICT(403, "이미 다른 역할로 워크스페이스에 참여 중인 멤버입니다.");

  private final int status;
  private final String defaultMessage;

  @Override
  public String code() {
    return name();
  }

  @Override
  public int status() {
    return status;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }
}
