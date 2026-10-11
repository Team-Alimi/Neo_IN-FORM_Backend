package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UpstageArticleSummaryClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.upstage.ai/v1");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("Solar Pro 4 에 text-only 요청을 한 번 보내고 usage 를 읽는다")
    void sendsOneBoundedRequest() {
        ArticleSummarySource source = new ArticleSummarySource(1L, "공지", "본문",
                java.time.LocalDate.of(2026, 10, 11), java.time.LocalDate.of(2026, 10, 15),
                3L, SummaryTestFixtures.SOURCE.updatedAt());
        UpstageArticleSummaryClient client = new UpstageArticleSummaryClient(
                builder.build(), "unit-test-placeholder", SummaryTestFixtures.properties());
        server.expect(requestTo("https://api.upstage.ai/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer unit-test-placeholder"))
                .andExpect(jsonPath("$.messages[1].content",
                        org.hamcrest.Matchers.containsString("\"starts_on\":\"2026-10-11\"")))
                .andExpect(jsonPath("$.messages[1].content",
                        org.hamcrest.Matchers.containsString("\"ends_on\":\"2026-10-15\"")))
                .andExpect(content().json("""
                        {"model":"solar-pro4","temperature":0,"max_tokens":512,"reasoning_effort":"none",
                         "response_format":{"type":"json_object"}}
                        """))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"content":"{\\"lines\\":[\\"첫 문장입니다.\\",\\"두 번째입니다.\\",\\"세 번째입니다.\\"]}"}}],
                         "usage":{"prompt_tokens":120,"completion_tokens":30}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.isEnabled()).isTrue();
        ArticleSummaryClient.Result result = client.generate(source);
        assertThat(result.totalTokens()).isEqualTo(150);
        assertThat(result.content()).contains("첫 문장입니다.");
        assertThat(client.reservationTokens(SummaryTestFixtures.SOURCE)).isGreaterThan(512);
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 생성기가 비활성화되며 HTTP 호출이 없다")
    void noCredentialDoesNotCall() {
        UpstageArticleSummaryClient client = new UpstageArticleSummaryClient(
                builder.build(), "", SummaryTestFixtures.properties());
        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.generate(SummaryTestFixtures.SOURCE))
                .isInstanceOf(ArticleSummaryClient.Failure.class);
        server.verify();
    }

    @Test
    @DisplayName("401 은 즉시 종료하고 429 는 재시도 대상으로 분류하며 응답 본문을 노출하지 않는다")
    void classifiesFailuresWithoutExposingProviderBody() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS}) {
            RestClient.Builder localBuilder = RestClient.builder().baseUrl("https://api.upstage.ai/v1");
            MockRestServiceServer localServer = MockRestServiceServer.bindTo(localBuilder).build();
            UpstageArticleSummaryClient client = new UpstageArticleSummaryClient(
                    localBuilder.build(), "unit-test-placeholder", SummaryTestFixtures.properties());
            localServer.expect(requestTo("https://api.upstage.ai/v1/chat/completions"))
                    .andRespond(withStatus(status).body("provider-private-body"));
            assertThatThrownBy(() -> client.generate(SummaryTestFixtures.SOURCE))
                    .isInstanceOfSatisfying(ArticleSummaryClient.Failure.class,
                            error -> {
                                assertThat(error.isRetryable()).isEqualTo(status == HttpStatus.TOO_MANY_REQUESTS);
                                assertThat(error.getMessage()).doesNotContain("provider-private-body", "unit-test-placeholder");
                                assertThat(error.getCause()).isNull();
                            });
            localServer.verify();
        }
    }
}
