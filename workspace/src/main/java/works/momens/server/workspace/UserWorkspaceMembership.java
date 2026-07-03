package works.momens.server.workspace;

import java.util.UUID;

/**
 * 사용자가 가진 workspace 멤버십 한 건의 조회 결과.
 *
 * <p>{@link WorkspaceMembership}이 workspace 스코프 조회(workspace의 멤버가 누구인지, MOM-61)라면, 이 record는 사용자
 * 스코프 조회(사용자가 어느 workspace에 어떤 역할로 속했는지)입니다. 모바일 bootstrap이 project의 role을 소속 workspace 멤버십 role로
 * 매핑할 때 사용합니다(MOM-60).
 *
 * <p>{@code role}은 멤버십 역할({@code owner}/{@code admin}/{@code member})입니다. task의 기능 역할(pm, android
 * 등)과는 구분되는 개념입니다.
 */
public record UserWorkspaceMembership(UUID workspaceId, String role) {}
