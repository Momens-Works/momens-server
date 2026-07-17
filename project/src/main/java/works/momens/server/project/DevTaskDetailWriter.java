package works.momens.server.project;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.config.DevOnly;

/**
 * dev 시연용 태스크 상세 writer.
 *
 * <p>일반 태스크 수정 계약이 소유하지 않는 다음행동과 열린질문까지 데모 데이터로 채우기 위한 한정 경로다. 구현은 {@code @DevOnly}로 게이트되어 prod에는
 * 등록되지 않고, 호출자의 태스크 생성 트랜잭션에 합류한다.
 */
@DevOnly
@Component
@RequiredArgsConstructor
public class DevTaskDetailWriter {

  private final EntityManager entityManager;
  private final JdbcClient jdbcClient;

  @Transactional(propagation = Propagation.MANDATORY)
  public void enrich(
      UUID taskId,
      UUID assigneeId,
      String description,
      String nextAction,
      List<String> checklistItems,
      List<String> openQuestions) {
    // TaskCreator의 JPA insert를 먼저 실행해 아래 JDBC update와 자식 FK insert가 같은 행을 보게 한다.
    entityManager.flush();
    jdbcClient
        .sql(
            "UPDATE tasks SET description = :description, assignee_id = :assigneeId, "
                + "next_action = :nextAction, updated_at = NOW() WHERE id = :taskId")
        .param("description", description)
        .param("assigneeId", assigneeId)
        .param("nextAction", nextAction)
        .param("taskId", taskId)
        .update();

    for (int position = 0; position < checklistItems.size(); position++) {
      jdbcClient
          .sql(
              "INSERT INTO task_checklist_items "
                  + "(id, task_id, title, completed, position) "
                  + "VALUES (:id, :taskId, :title, false, :position)")
          .param("id", UUID.randomUUID())
          .param("taskId", taskId)
          .param("title", checklistItems.get(position))
          .param("position", position)
          .update();
    }

    for (int sortOrder = 0; sortOrder < openQuestions.size(); sortOrder++) {
      jdbcClient
          .sql(
              "INSERT INTO task_open_questions (id, task_id, body, sort_order) "
                  + "VALUES (:id, :taskId, :body, :sortOrder)")
          .param("id", UUID.randomUUID())
          .param("taskId", taskId)
          .param("body", openQuestions.get(sortOrder))
          .param("sortOrder", sortOrder)
          .update();
    }
  }
}
