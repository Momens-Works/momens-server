package works.momens.server.workspace;

import java.util.UUID;

/**
 * workspace 멤버십 한 건의 조회 결과.
 *
 * <p>{@code project} 모듈이 프로젝트 멤버 목록을 조회할 때 이 값으로 멤버의 userId와 역할을 받습니다(MOM-61). 이름과 아바타 같은 사용자 정보는
 * workspace가 아니라 user 모듈이 소유하므로, 호출하는 쪽에서 user public API를 통해 결합합니다. 사용자 스코프 조회(사용자가 속한 workspace
 * 목록)는 {@link UserWorkspaceMembership}이 담당합니다.
 *
 * <p>{@code role}은 멤버십 역할({@code owner}/{@code admin}/{@code member})입니다. task의 기능 역할(pm, android
 * 등)과는 구분되는 개념입니다.
 */
public record WorkspaceMembership(UUID userId, String role) {}
