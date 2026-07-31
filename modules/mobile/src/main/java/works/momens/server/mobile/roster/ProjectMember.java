package works.momens.server.mobile.roster;

import java.util.UUID;

/**
 * 담당자 선택 bottom sheet에 내려줄 프로젝트 멤버 한 명(조합 결과).
 *
 * <p>presentation 응답 DTO와 분리된 내부 값입니다. role은 명세(docs/spec/mobile-api.md 프로젝트 멤버 절)와 담당자 선택 화면이 쓰지
 * 않아 담지 않습니다.
 */
public record ProjectMember(UUID id, String name, String avatarUrl) {}
