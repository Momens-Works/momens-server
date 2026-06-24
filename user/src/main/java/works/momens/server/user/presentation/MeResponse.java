package works.momens.server.user.presentation;

import works.momens.server.user.UserProfile;

/** {@code {"user": {...}}} 래퍼. 레거시 {@code GET/PATCH /auth/me}의 응답 shape를 보존합니다. */
record MeResponse(UserResponse user) {

  static MeResponse from(UserProfile profile) {
    return new MeResponse(UserResponse.from(profile));
  }
}
