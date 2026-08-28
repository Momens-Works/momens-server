package works.momens.server.mobile.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.mobile.auth.dto.request.GoogleTokenRequest;
import works.momens.server.mobile.auth.dto.request.LogoutRequest;
import works.momens.server.mobile.auth.dto.request.RefreshTokenRequest;
import works.momens.server.mobile.auth.dto.response.AuthMessageResponse;
import works.momens.server.mobile.auth.dto.response.TokenResponse;

/**
 * 인증 API 문서. 이 엔드포인트들은 모두 공개이므로(요청 본문의 자격 증명으로 동작하고 SecurityConfig의 공개 체인에 속한다) OpenApiConfig가 적용한
 * 전역 Bearer 요구에서 {@code @SecurityRequirements}(빈 값)로 제외한다. 이렇게 해야 문서에도 인증 없이 호출하는 엔드포인트로
 * 표시된다(MOM-83).
 */
@Tag(name = "Auth", description = "인증 API")
interface AuthControllerDocs {

  @Operation(
      summary = "모바일 Google ID 토큰 교환",
      description = "Google ID 토큰을 검증하고 Momens access/refresh token을 발급합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "토큰 발급 성공",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @SecurityRequirements
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  TokenResponse loginWithGoogleToken(GoogleTokenRequest request);

  @Operation(
      summary = "Access token 재발급",
      description = "Refresh token을 회전하고 새 access/refresh token을 발급합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "토큰 재발급 성공",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @SecurityRequirements
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  TokenResponse refresh(RefreshTokenRequest request);

  @Operation(summary = "로그아웃", description = "Refresh token을 폐기합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "로그아웃 성공",
      content = @Content(schema = @Schema(implementation = AuthMessageResponse.class)))
  @SecurityRequirements
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  AuthMessageResponse logout(LogoutRequest request);
}
