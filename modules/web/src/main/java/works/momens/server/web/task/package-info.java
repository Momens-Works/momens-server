/**
 * 웹 태스크 하위 도메인입니다.
 *
 * <p>레거시 웹 클라이언트가 사용하는 태스크 목록, 상세, 수정, 컨텍스트 조회를 조합하고 태스크 자체의 변경과 태스크에 연결된 엔티티의 변경을 처리합니다. {@code
 * project}, {@code context}, {@code memory}, {@code source}, {@code workspace} 모듈의 public API만 사용하며
 * 도메인 정책은 소유하지 않습니다.
 *
 * <p>컨트롤러는 세 개로 구분합니다. 조회와 태스크 자체의 변경은 각각 {@code TaskReadController}와 {@code TaskWriteController}가
 * 담당하고, 태스크와 다른 엔티티 사이의 연결은 {@code TaskLinkController}가 담당합니다. 앞의 두 컨트롤러는 태스크 행의 컬럼을 읽고 쓰지만 {@code
 * TaskLinkController}는 {@code entity_relations} 행을 생성하고 삭제하므로 변경 대상이 다릅니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.web.task;
