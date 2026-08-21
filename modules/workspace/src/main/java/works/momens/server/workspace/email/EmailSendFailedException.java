package works.momens.server.workspace.email;

public class EmailSendFailedException extends RuntimeException {

  public EmailSendFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
