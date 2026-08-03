package works.momens.server.minsu.internal.ledger;

import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraftAsyncIntent;

/**
 * 적재를 기다리는 비동기 생성 의사.
 *
 * <p>draft 확보 시점에 만들어져 호출자를 거쳐 적재 시점으로 돌아온다. 담는 것은 convert 시점 입력 snapshot뿐이고(5.6절), 실행 시점에 Signal을
 * 다시 읽지 않으므로 사용자가 convert를 누른 시점의 근거가 그대로 보존된다.
 *
 * <p>이 타입은 패키지 밖으로 나가지 않는다. 호출자는 {@link TaskDraftAsyncIntent}로만 들고 다닌다.
 */
record LedgerEnrollment(SignalTaskDraftInput snapshot) implements TaskDraftAsyncIntent {}
