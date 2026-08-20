package works.momens.server.web.task.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;
import java.util.UUID;

/** PATCH의 누락과 null을 구분하는 웹 task 요청입니다. */
public final class UpdateWebTaskRequest {
  private String title;
  private boolean titleSet;
  private String description;
  private boolean descriptionSet;
  private String status;
  private boolean statusSet;
  private String priority;
  private boolean prioritySet;
  private UUID milestoneId;
  private boolean milestoneIdSet;
  private UUID assigneeId;
  private boolean assigneeIdSet;
  private LocalDate dueDate;
  private boolean dueDateSet;

  @JsonSetter("title")
  public void setTitle(String value) {
    title = value;
    titleSet = true;
  }

  @JsonSetter("description")
  public void setDescription(String value) {
    description = value;
    descriptionSet = true;
  }

  @JsonSetter("status")
  public void setStatus(String value) {
    status = value;
    statusSet = true;
  }

  @JsonSetter("priority")
  public void setPriority(String value) {
    priority = value;
    prioritySet = true;
  }

  @JsonSetter("milestone_id")
  public void setMilestoneId(UUID value) {
    milestoneId = value;
    milestoneIdSet = true;
  }

  @JsonSetter("assignee_id")
  public void setAssigneeId(UUID value) {
    assigneeId = value;
    assigneeIdSet = true;
  }

  @JsonSetter("due_date")
  public void setDueDate(LocalDate value) {
    dueDate = value;
    dueDateSet = true;
  }

  public String title() {
    return title;
  }

  public boolean titleSet() {
    return titleSet;
  }

  public String description() {
    return description;
  }

  public boolean descriptionSet() {
    return descriptionSet;
  }

  public String status() {
    return status;
  }

  public boolean statusSet() {
    return statusSet;
  }

  public String priority() {
    return priority;
  }

  public boolean prioritySet() {
    return prioritySet;
  }

  public UUID milestoneId() {
    return milestoneId;
  }

  public boolean milestoneIdSet() {
    return milestoneIdSet;
  }

  public UUID assigneeId() {
    return assigneeId;
  }

  public boolean assigneeIdSet() {
    return assigneeIdSet;
  }

  public LocalDate dueDate() {
    return dueDate;
  }

  public boolean dueDateSet() {
    return dueDateSet;
  }
}
