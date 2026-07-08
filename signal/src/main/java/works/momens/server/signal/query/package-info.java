/**
 * Signal 조회 하위 도메인.
 *
 * <p>signal 모듈 안에서 Signal 원본 조회(목록/상세)가 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-65). 외부에는 이
 * 패키지의 조회 서비스({@link works.momens.server.signal.query.SignalListService}, {@link
 * works.momens.server.signal.query.SignalDetailService})와 응답 조립용 레코드만 공개하고, 엔티티·리포지토리는 이 패키지 안에
 * 은닉합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.signal.query;
