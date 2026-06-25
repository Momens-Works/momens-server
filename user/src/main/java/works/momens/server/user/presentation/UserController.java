package works.momens.server.user.presentation;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.user.presentation.dto.request.UpdateMeRequest;
import works.momens.server.user.presentation.dto.response.MeResponse;

/**
 * 현재 사용자 프로필 엔드포인트.
 *
 * <p>공식 path는 {@code /api/me}이고, 레거시 호환 {@code /me}는 같은 handler의
 * alias입니다(docs/spec/api-versioning.md). 현재 사용자는 {@link Principal}로 받습니다({@code getName()} =
 * userId). 인증 수단에 중립이며, SecurityContext를 채우는 배선은 MOM-8(auth)에서 추가됩니다.
 */
@RestController
@RequiredArgsConstructor
class UserController implements UserControllerDocs {

  private final UserService userService;

  @Override
  @GetMapping({"/api/me", "/me"})
  public MeResponse getMe(Principal principal) {
    UserProfile profile = userService.getProfile(currentUserId(principal));
    return MeResponse.from(profile);
  }

  @Override
  @PatchMapping({"/api/me", "/me"})
  public MeResponse updateMe(Principal principal, @Valid @RequestBody UpdateMeRequest request) {
    UserProfile profile =
        userService.updateProfile(currentUserId(principal), request.name(), request.jobRole());
    return MeResponse.from(profile);
  }

  private static UUID currentUserId(Principal principal) {
    if (principal == null) {
      throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
    }
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BusinessException(CommonErrorCode.AUTH_INVALID_TOKEN);
    }
  }
}
