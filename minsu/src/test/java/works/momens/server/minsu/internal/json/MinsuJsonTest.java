package works.momens.server.minsu.internal.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MinsuJsonTest {

  @Test
  void readsJsonNullAsEmpty() {
    assertThat(new MinsuJson().read("null", Draft.class)).isEmpty();
  }

  private record Draft(String title) {}
}
