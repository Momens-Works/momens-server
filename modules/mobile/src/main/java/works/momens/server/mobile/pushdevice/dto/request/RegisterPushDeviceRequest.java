package works.momens.server.mobile.pushdevice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 설치 등록 또는 token 갱신 요청(docs/design/signal-push-demo-design.md 8.2절). */
@Schema(description = "push 설치 등록 또는 FCM token 갱신 요청")
public record RegisterPushDeviceRequest(
    @Schema(description = "현재 FCM registration token") @NotBlank String fcmRegistrationToken,
    @Schema(description = "설치 platform. 이번 범위에서는 android만 허용", example = "android")
        @NotBlank
        @Pattern(regexp = "android", message = "platform은 android만 허용합니다.")
        String platform) {}
