package works.momens.server.minsu.internal.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.internal.llm.LlmResponse;

class DefaultGoogleSdkClientTest {

  @Test
  void mapsSdkResponseToVendorNeutralResponse() {
    GenerateContentResponse sdkResponse =
        GenerateContentResponse.fromJson(
            """
            {
              "candidates": [{
                "content": {"parts": [{"text": "{\\"title\\":\\"점검\\",\\"role\\":\\"pm\\",\\"priority\\":\\"medium\\"}"}]},
                "finishReason": "STOP"
              }],
              "responseId": "response-1",
              "usageMetadata": {
                "promptTokenCount": 10,
                "candidatesTokenCount": 5,
                "thoughtsTokenCount": 2,
                "totalTokenCount": 17
              }
            }
            """);

    LlmResponse response = DefaultGoogleSdkClient.map(sdkResponse);

    assertThat(response.candidatePresent()).isTrue();
    assertThat(response.finishReason()).isEqualTo("STOP");
    assertThat(response.text()).contains("\"title\":\"점검\"");
    assertThat(response.responseId()).isEqualTo("response-1");
    assertThat(response.tokenUsage()).isEqualTo(new LlmResponse.TokenUsage(10, 5, 2, 17));
  }

  @Test
  void mapsMissingCandidateWithoutThrowing() {
    GenerateContentResponse sdkResponse =
        GenerateContentResponse.fromJson(
            """
            {
              "responseId": "response-2",
              "usageMetadata": {"totalTokenCount": 3}
            }
            """);

    LlmResponse response = DefaultGoogleSdkClient.map(sdkResponse);

    assertThat(response.candidatePresent()).isFalse();
    assertThat(response.responseId()).isEqualTo("response-2");
    assertThat(response.tokenUsage().total()).isEqualTo(3);
  }

  @Test
  void mapsSafetyFinishReasonWithoutThrowing() {
    GenerateContentResponse sdkResponse =
        GenerateContentResponse.fromJson(
            """
            {
              "candidates": [{
                "finishReason": "SAFETY"
              }],
              "responseId": "response-3",
              "usageMetadata": {
                "promptTokenCount": 10,
                "totalTokenCount": 10
              }
            }
            """);

    LlmResponse response = DefaultGoogleSdkClient.map(sdkResponse);

    assertThat(response.candidatePresent()).isTrue();
    assertThat(response.finishReason()).isEqualTo("SAFETY");
    assertThat(response.text()).isEmpty();
    assertThat(response.responseId()).isEqualTo("response-3");
    assertThat(response.tokenUsage()).isEqualTo(new LlmResponse.TokenUsage(10, 0, 0, 10));
  }
}
