package works.momens.server.auth.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.auth.presentation.dto.request.GoogleTokenRequest;
import works.momens.server.auth.presentation.dto.request.LogoutRequest;
import works.momens.server.auth.presentation.dto.request.RefreshTokenRequest;
import works.momens.server.auth.presentation.dto.response.AuthMessageResponse;
import works.momens.server.auth.presentation.dto.response.TokenResponse;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;

@Tag(name = "Auth", description = "인증 API")
interface AuthControllerDocs {

  @Operation(
      summary = "모바일 Google ID 토큰 교환",
      description = "Google ID 토큰을 검증하고 Momens access/refresh token을 발급합니다.",
      parameters =
          @Parameter(name = "API-Version", in = ParameterIn.HEADER, required = true, example = "1"))
  @ApiResponse(
      responseCode = "200",
      description = "토큰 발급 성공",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  TokenResponse loginWithGoogleToken(GoogleTokenRequest request);

  @Operation(
      summary = "Access token 재발급",
      description = "Refresh token을 회전하고 새 access/refresh token을 발급합니다.",
      parameters =
          @Parameter(name = "API-Version", in = ParameterIn.HEADER, required = true, example = "1"))
  @ApiResponse(
      responseCode = "200",
      description = "토큰 재발급 성공",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  TokenResponse refresh(RefreshTokenRequest request);

  @Operation(
      summary = "로그아웃",
      description = "Refresh token을 폐기합니다.",
      parameters =
          @Parameter(name = "API-Version", in = ParameterIn.HEADER, required = true, example = "1"))
  @ApiResponse(
      responseCode = "200",
      description = "로그아웃 성공",
      content = @Content(schema = @Schema(implementation = AuthMessageResponse.class)))
  @ApiExceptions({AuthErrorCode.class, CommonErrorCode.class})
  AuthMessageResponse logout(LogoutRequest request);
}
