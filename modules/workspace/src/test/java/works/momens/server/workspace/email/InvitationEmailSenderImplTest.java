package works.momens.server.workspace.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvitationEmailSenderImplTest {

  private final List<EmailMessage> sent = new ArrayList<>();

  private InvitationEmailSender sender(String acceptUrl) {
    return new InvitationEmailSenderImpl(
        sent::add,
        new InvitationEmailProperties(acceptUrl, "no-reply@momens.works", "help@momens.works"));
  }

  private InvitationEmail invitation(String inviterName) {
    return new InvitationEmail(
        "모먼스<script>",
        inviterName,
        "gyuil@momens.works",
        "jinsu@momens.works",
        "member",
        "raw-token",
        Instant.parse("2026-08-28T09:05:03Z"));
  }

  @Test
  void rendersLegacyTemplateValues() {
    sender("https://app.momens.works/invite").send(invitation(""));

    EmailMessage message = sent.getFirst();
    assertThat(message.to()).isEqualTo("jinsu@momens.works");
    assertThat(message.subject()).isEqualTo("You're invited to join 모먼스<script> on Momens");
    assertThat(message.text())
        .contains("A workspace admin invited you to join 모먼스<script> on Momens")
        .contains("https://app.momens.works/invite?token=raw-token")
        .contains("Fri, 28 Aug 2026 09:05:03 UTC")
        .contains("gyuil@momens.works")
        .contains("help@momens.works");
    assertThat(message.html()).doesNotContain("{{.").contains("모먼스&lt;script&gt;");
  }
}
