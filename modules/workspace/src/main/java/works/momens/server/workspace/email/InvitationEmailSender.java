package works.momens.server.workspace.email;

/**
 * 워크스페이스 초대 이메일 발송 경계입니다.
 *
 * <p>초대 하위 도메인은 발송할 정보만 전달하며 이메일 본문 구성과 실제 발송 방식은 알지 못합니다. 발송에 실패하면 {@link
 * EmailSendFailedException}을 던집니다.
 */
public interface InvitationEmailSender {

  void send(InvitationEmail invitation);
}
