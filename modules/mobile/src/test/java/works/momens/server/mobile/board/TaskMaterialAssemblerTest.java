package works.momens.server.mobile.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.context.EntityRelationReader;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;

@ExtendWith(MockitoExtension.class)
class TaskMaterialAssemblerTest {

  @Mock private EntityRelationReader entityRelationReader;
  @Mock private SourceRefReader sourceRefReader;
  @InjectMocks private TaskMaterialAssembler taskMaterialAssembler;

  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-06-28T00:48:00Z");

  @Test
  void getMaterialsKeepsLinkOrderAndSkipsMissingSourceRefInTwoBatchQueries() {
    UUID taskId = UUID.randomUUID();
    UUID figma = UUID.randomUUID();
    UUID slack = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    List<UUID> sourceRefIds = List.of(figma, slack, gone);
    when(entityRelationReader.findLinkedSourceRefIds(WORKSPACE_ID, List.of(taskId)))
        .thenReturn(Map.of(taskId, sourceRefIds));
    when(sourceRefReader.findByIds(WORKSPACE_ID, sourceRefIds))
        .thenReturn(
            List.of(
                sourceRef(slack, "slack", "스레드", null, "본문 전체"),
                sourceRef(figma, "figma", "권한 요청 화면 v2", "설명 문구 변경", "본문")));

    List<MobileTaskDetail.Material> materials =
        taskMaterialAssembler.getMaterials(WORKSPACE_ID, taskId);

    assertThat(materials).extracting(MobileTaskDetail.Material::id).containsExactly(figma, slack);
    assertThat(materials.getFirst().summary()).isEqualTo("설명 문구 변경");
    assertThat(materials.get(1).summary()).isEqualTo("본문 전체");
    verify(entityRelationReader).findLinkedSourceRefIds(WORKSPACE_ID, List.of(taskId));
    verify(sourceRefReader).findByIds(WORKSPACE_ID, sourceRefIds);
  }

  @Test
  void countMaterialsUsesSameLiveSourceCriterionInTwoBatchQueries() {
    UUID linked = UUID.randomUUID();
    UUID unlinked = UUID.randomUUID();
    UUID live = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    List<UUID> taskIds = List.of(linked, unlinked);
    List<UUID> sourceRefIds = List.of(live, gone);
    when(entityRelationReader.findLinkedSourceRefIds(WORKSPACE_ID, taskIds))
        .thenReturn(Map.of(linked, sourceRefIds));
    when(sourceRefReader.findByIds(WORKSPACE_ID, sourceRefIds))
        .thenReturn(List.of(sourceRef(live, "figma", "제목", "요약", "본문")));

    Map<UUID, Integer> counts = taskMaterialAssembler.countMaterials(WORKSPACE_ID, taskIds);

    assertThat(counts.entrySet())
        .extracting(Map.Entry::getKey, Map.Entry::getValue)
        .containsExactly(tuple(linked, 1));
    assertThat(counts.getOrDefault(unlinked, 0)).isZero();
    verify(entityRelationReader).findLinkedSourceRefIds(WORKSPACE_ID, taskIds);
    verify(sourceRefReader).findByIds(WORKSPACE_ID, sourceRefIds);
  }

  private static SourceRefView sourceRef(
      UUID id, String sourceType, String title, String snippet, String text) {
    return new SourceRefView(
        id, sourceType, title, snippet, text, "https://source.example", OCCURRED_AT);
  }
}
