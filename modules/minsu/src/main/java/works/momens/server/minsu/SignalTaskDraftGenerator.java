package works.momens.server.minsu;

/** Signal 근거로 검증된 task draft를 생성하는 Minsu 공개 유스케이스. */
public interface SignalTaskDraftGenerator {

  TaskDraft generate(SignalTaskDraftInput input);
}
