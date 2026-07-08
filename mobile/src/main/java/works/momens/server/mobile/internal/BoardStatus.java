package works.momens.server.mobile.internal;

import java.util.Arrays;
import java.util.List;

/**
 * 모바일 보드가 노출하는 상태. 어떤 상태를 어떤 순서와 라벨로 보일지는 모바일 표면이 소유합니다(module-map 참고). 상태 키와 화면 라벨, 표시 순서를 한곳에 모아
 * 조회 필터와 그룹 구성, 응답 라벨이 서로 어긋나지 않게 합니다.
 *
 * <p>선언 순서가 곧 보드 그룹 순서입니다. 수정 화면이 상태 5종을 모두 편집하고, backlog나 cancelled로 바꾼 태스크가 보드에서 사라지지 않도록 5종을 모두
 * 담습니다(MOM-75).
 */
public enum BoardStatus {
  BACKLOG("backlog", "백로그"),
  TODO("todo", "투두"),
  IN_PROGRESS("in_progress", "진행중"),
  DONE("done", "완료"),
  CANCELLED("cancelled", "취소");

  private final String key;
  private final String label;

  BoardStatus(String key, String label) {
    this.key = key;
    this.label = label;
  }

  /** 도메인 상태 문자열. 응답의 group_key이자 project 조회 필터로 넘기는 값입니다. */
  public String key() {
    return key;
  }

  /** 화면에 보이는 그룹 이름입니다. */
  public String label() {
    return label;
  }

  /** project 조회에 넘길 보드 상태 키 목록입니다. */
  public static List<String> keys() {
    return Arrays.stream(values()).map(BoardStatus::key).toList();
  }
}
