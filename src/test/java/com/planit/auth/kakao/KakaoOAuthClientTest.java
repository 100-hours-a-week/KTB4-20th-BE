package com.planit.auth.kakao;

import com.planit.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KakaoOAuthClientTest {

    private MockRestServiceServer server;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        AuthProperties properties = new AuthProperties(
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                false,
                "planit_oauth_state",
                Duration.ofMinutes(10),
                new AuthProperties.Jwt(
                        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                        "planit-auth",
                        "planit-api",
                        Duration.ofMinutes(15)
                ),
                new AuthProperties.Kakao(
                        "test-client-id",
                        "test-client-secret",
                        "test-admin-key",
                        URI.create(
                                "http://localhost:8080"
                                        + "/api/auth/oauth/callback"
                        ),
                        URI.create(
                                "https://kauth.kakao.com"
                                        + "/oauth/authorize"
                        ),
                        URI.create(
                                "https://kauth.kakao.com"
                                        + "/oauth/token"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v2/user/me"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v1/user/unlink"
                        )
                )
        );

        kakaoOAuthClient = new KakaoOAuthClient(
                builder.build(),
                properties
        );
    }

    @Test
    void exchangesAuthorizationCodeForAccessToken() {
        MultiValueMap<String, String> expectedForm =
                new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "test-client-id");
        expectedForm.add(
                "redirect_uri",
                "http://localhost:8080/api/auth/oauth/callback"
        );
        expectedForm.add("code", "test-authorization-code");
        expectedForm.add(
                "client_secret",
                "test-client-secret"
        );

        server.expect(requestTo(
                        "https://kauth.kakao.com/oauth/token"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_FORM_URLENCODED
                ))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(
                        """
                        {
                          "token_type": "bearer",
                          "access_token": "kakao-access-token",
                          "expires_in": 43199,
                          "refresh_token": "kakao-refresh-token",
                          "refresh_token_expires_in": 5184000
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        String accessToken = kakaoOAuthClient.exchangeCode(
                "test-authorization-code"
        );

        assertThat(accessToken)
                .isEqualTo("kakao-access-token");
        server.verify();
    }

    @Test
    void getsKakaoUserIdAndNickname() {
        server.expect(requestTo(
                        "https://kapi.kakao.com/v2/user/me"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer kakao-access-token"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "id": 123456789,
                          "connected_at": "2026-09-20T10:00:00Z",
                          "kakao_account": {
                            "profile_nickname_needs_agreement": false,
                            "profile": {
                              "nickname": "플랜잇사용자",
                              "is_default_nickname": false
                            }
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        KakaoUserResponse user = kakaoOAuthClient.getUser(
                "kakao-access-token"
        );

        assertThat(user.providerUserId())
                .isEqualTo("123456789");
        assertThat(user.nickname())
                .isEqualTo("플랜잇사용자");
        server.verify();
    }

    @Test
    void unlinksKakaoUserWithAdminKey() {
        MultiValueMap<String, String> expectedForm =
                new LinkedMultiValueMap<>();
        expectedForm.add("target_id_type", "user_id");
        expectedForm.add("target_id", "123456789");

        server.expect(requestTo(
                        "https://kapi.kakao.com/v1/user/unlink"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "KakaoAK test-admin-key"
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_FORM_URLENCODED
                ))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(
                        "{\"id\":123456789}",
                        MediaType.APPLICATION_JSON
                ));

        kakaoOAuthClient.unlink("123456789");

        server.verify();
    }

    @Test
    void treatsAlreadyUnlinkedKakaoUserAsSuccess() {
        server.expect(requestTo(
                        "https://kapi.kakao.com/v1/user/unlink"
                ))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-101}"));

        assertThatCode(() -> kakaoOAuthClient.unlink("123456789"))
                .doesNotThrowAnyException();

        server.verify();
    }
}
