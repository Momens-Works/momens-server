package works.momens.server.source;

/**
 * 외부 source 연결의 OAuth 승인 흐름을 담당하는 public API입니다.
 *
 * <p>승인 흐름은 두 단계로 구성됩니다. {@code beginInstall}은 사용자가 이동할 provider 승인 URL을 생성하고, {@code
 * completeInstall}은 provider가 반환한 승인 결과를 받아 연결 정보와 토큰을 저장합니다. 두 단계는 서명된 state로 연결됩니다. 두 번째 단계에는 로그인
 * 정보가 없으므로 요청자는 state만으로 판정합니다.
 *
 * <p>이 API는 권한을 확인하지 않습니다. 워크스페이스 존재 여부와 요청자의 역할은 이 API를 호출하는 {@code :web} 모듈에서 확인합니다.
 */
public interface SourceInstaller {

  /**
   * provider 승인 화면으로 이동할 URL을 생성해 반환합니다.
   *
   * <p>지원하지 않는 provider이거나 서버에 해당 provider 설정이 없으면 예외를 던집니다.
   */
  String beginInstall(BeginInstallCommand command);

  /**
   * provider가 반환한 승인 결과로 토큰을 교환하고, 연결 정보와 자격 증명을 하나의 트랜잭션으로 저장합니다.
   *
   * <p>같은 워크스페이스에서 같은 외부 계정을 다시 승인하면 새 연결을 생성하지 않고 기존 연결을 갱신합니다.
   */
  CompletedInstall completeInstall(CompleteInstallCommand command);
}
