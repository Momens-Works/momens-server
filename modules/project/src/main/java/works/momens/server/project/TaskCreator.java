package works.momens.server.project;

/**
 * task 생성 public API.
 *
 * <p>모바일 일반 태스크 생성(MOM-62)이 사용합니다. 생성 시 workspace 범위 {@code MOM} 라벨을 발급하고, 새 태스크는 {@code todo} 상태로
 * 시작합니다.
 */
public interface TaskCreator {

  CreatedTask create(CreateTaskCommand command);
}
