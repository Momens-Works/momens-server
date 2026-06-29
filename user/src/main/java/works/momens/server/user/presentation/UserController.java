package works.momens.server.user.presentation;

import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.user.presentation.dto.request.UpdateMeRequest;
import works.momens.server.user.presentation.dto.response.MeResponse;

/**
 * 현재 사용자 프로필 엔드포인트.
 *
 * <p>path는 {@code /api/me} 단일 경로이며 {@code version = "1"} mapping을 둡니다(레거시 path alias 없음,
 * docs/spec/api-versioning.md). 현재 사용자는 {@link CurrentUser#id(Principal)}로 읽습니다({@code
 * Principal.getName()} = userId). 인증 수단에 중립이며, SecurityContext를 채우는 배선은 auth(MOM-8)가 담당합니다.
 */
@RestController
@RequiredArgsConstructor
class UserController implements UserControllerDocs {

  private final UserService userService;

  @Override
  @GetMapping(path = "/api/me", version = "1")
  public MeResponse getMe(Principal principal) {
    UserProfile profile = userService.getProfile(CurrentUser.id(principal));
    return MeResponse.from(profile);
  }

  @Override
  @PatchMapping(path = "/api/me", version = "1")
  public MeResponse updateMe(Principal principal, @Valid @RequestBody UpdateMeRequest request) {
    UserProfile profile =
        userService.updateProfile(CurrentUser.id(principal), request.name(), request.jobRole());
    return MeResponse.from(profile);
  }
}
