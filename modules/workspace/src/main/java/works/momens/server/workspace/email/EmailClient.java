package works.momens.server.workspace.email;

/**
 * 이메일을 외부로 발송하는 경계입니다.
 *
 * <p>발송할 이메일을 구성하는 책임과 외부 이메일 서비스로 전달하는 책임을 분리합니다. 이메일 종류가 늘어나면 발송할 내용을 구성하는 구현을 추가하고, 외부 이메일 서비스를
 * 변경하면 이 인터페이스의 구현만 교체합니다.
 */
interface EmailClient {

  void send(EmailMessage message);
}
