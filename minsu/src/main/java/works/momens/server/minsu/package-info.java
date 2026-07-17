/**
 * 민수(Minsu) 모듈 공개 API.
 *
 * <p>Signal 컨텍스트(제목·근거)를 입력받아 태스크 등록용 draft(title·role·priority)와 상세 화면 suggestion을 생성하는 서버 내부
 * 능력입니다(ADR-0011, MOM-0692). 실제 생성은 GCP Vertex AI(Gemini)로 하며 인증은 ADC를 씁니다(레거시 momens-api의 llm 패턴과
 * 동일: API 키 없음).
 *
 * <p>모듈 root package는 Spring Modulith 공개 표면입니다(docs/rules/architecture.md). 계약(인터페이스·DTO·에러 코드)만
 * 두고, Vertex 클라이언트·프롬프트·설정 구현은 {@code internal} nested 모듈에 은닉합니다. 소비자(signal)는 컨텍스트를 조립해 {@link
 * works.momens.server.minsu.Minsu}로만 호출하므로 민수는 다른 도메인 모듈에 의존하지 않습니다(순환 없음).
 *
 * <p>민수는 하드 의존입니다: 미설정·호출 실패 시 목값으로 폴백하지 않고 {@link works.momens.server.minsu.MinsuErrorCode} 에러를
 * 던집니다.
 */
package works.momens.server.minsu;
