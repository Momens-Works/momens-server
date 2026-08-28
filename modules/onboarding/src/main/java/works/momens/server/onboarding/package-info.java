/**
 * Onboarding 모듈의 공개 API입니다.
 *
 * <p>새 워크스페이스를 생성하고 바로 사용할 수 있도록 초기 데이터를 구성하는 작업을 소유합니다. 레거시가 워크스페이스 생성 트랜잭션에서 함께 저장하던 이름이 {@code
 * Welcome}인 프로젝트와 메모리 세 건({@code workspace/seed.go:40})이 대상입니다.
 *
 * <p>레거시의 {@code /workspaces/:id/onboarding} endpoint 두 개(H025, H026)와는 다른 기능입니다. 해당 endpoint는
 * 워크스페이스의 온보딩 진행 상태를 조회하고 수정하며, 웹에서 호출하지 않아 이관 대상에서 제외되었습니다(MOM-0863, PR #156). 해당 모듈은 온보딩 진행 상태를
 * 다루지 않습니다.
 *
 * <p>모듈 이름은 {@code momens-fe}가 첫 로그인 화면을 지칭하는 명칭에서 가져왔습니다({@code first-login.css}의 {@code
 * FIRST-LOGIN (workspace onboarding)}).
 *
 * <p>workspace, project, memory 모듈의 public API만 조합하며 자체 테이블은 소유하지 않습니다. 세 도메인 모듈이 서로 참조하지 않고도 하나의
 * 트랜잭션에 참여할 수 있도록 해당 모듈이 저장 순서와 트랜잭션 경계를 관리합니다.
 */
package works.momens.server.onboarding;
