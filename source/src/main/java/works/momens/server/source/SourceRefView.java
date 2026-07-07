package works.momens.server.source;

import java.time.Instant;
import java.util.UUID;

/**
 * source_ref 한 건의 조회 결과.
 *
 * <p>근거 카드가 필요로 하는 최소 read 모델입니다. Signal evidence(MOM-64)는 {@code sourceType}→source, {@code
 * title}→source_title, {@code sourceCreatedAt}→occurred_at, {@code sourceUrl}→source_url로 매핑하고,
 * summary는 {@code snippet}이 없으면 {@code text}로 폴백해 조립합니다. 화면 표시용 상대 시각 라벨과 provider별 fields는 원본 컬럼이
 * 아니라 조합 규칙이므로 이 모델에 두지 않고 호출하는 쪽이 만듭니다.
 */
public record SourceRefView(
    UUID id,
    String sourceType,
    String title,
    String snippet,
    String text,
    String sourceUrl,
    Instant sourceCreatedAt) {}
