/**
 * milestone 하위 도메인.
 *
 * <p>마일스톤과 소유자 aggregate, 생성·조회 영속성을 소유합니다. project core와는 core root의 공개 계약으로만 협력합니다. 워크스페이스
 * snapshot의 쿼리 예산을 유지하기 위한 {@code projects} 조인은 이 경계의 조회 저장소에만 허용합니다.
 */
@org.springframework.modulith.NamedInterface
package works.momens.server.project.milestone;
