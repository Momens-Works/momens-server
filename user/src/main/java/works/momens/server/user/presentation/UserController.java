package works.momens.server.user.presentation;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * 현재 사용자 프로필 엔드포인트({@code /me}).
 *
 * <p>"현재 사용자"는 {@link Principal} seam으로 받습니다({@code principal.getName()} = userId). 인증 수단(JWT/세션)에
 * 중립이며 user 모듈은 {@code auth}에 의존하지 않습니다. SecurityFilterChain이 SecurityContext를 채우는 배선은 인증 구현
 * 시점(MOM-8)에 추가됩니다.
 */
@RestController
@RequiredArgsConstructor
class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public MeResponse getMe(Principal principal) {
    UserProfile profile = userService.getProfile(currentUserId(principal));
    return MeResponse.from(profile);
  }

  @PatchMapping("/me")
  public MeResponse updateMe(Principal principal, @Valid @RequestBody UpdateMeRequest request) {
    UserProfile profile =
        userService.updateProfile(currentUserId(principal), request.name(), request.jobRole());
    return MeResponse.from(profile);
  }

  private static UUID currentUserId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}
