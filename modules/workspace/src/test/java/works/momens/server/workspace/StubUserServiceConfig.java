package works.momens.server.workspace;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import works.momens.server.user.UserService;

@TestConfiguration
public class StubUserServiceConfig {

  @Bean
  UserService userService() {
    return mock(UserService.class);
  }
}
