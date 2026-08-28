/**
 * Signal 조회 하위 도메인.
 *
 * <p>signal 모듈 안에서 Signal 원본 조회(목록/상세)가 담당하는 경계를 Spring Modulith nested 모듈로 명시합니다(MOM-65). 외부에는 모듈
 * root의 {@link works.momens.server.signal.SignalListService}와 {@link
 * works.momens.server.signal.SignalDetailService}로만 공개합니다. 엔티티·리포지토리·구현체는 이 패키지 안에 은닉합니다.
 */
@org.springframework.modulith.ApplicationModule
package works.momens.server.signal.query;
