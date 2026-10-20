package com.planit.auth.kakao;

import com.planit.auth.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
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

    @DisplayName("인가 코드로 카카오 액세스 토큰을 발급받는다")
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

    @DisplayName("카카오 사용자 ID와 닉네임을 조회한다")
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
                              "profile_image_url": "https://example.com/profile.jpg",
                              "is_default_image": false,
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
        assertThat(user.customProfileImageUrl())
                .isEqualTo("https://example.com/profile.jpg");
        server.verify();
    }

    @DisplayName("카카오 기본 프로필 이미지는 사용자 이미지로 사용하지 않는다")
    @Test
    void ignoresKakaoDefaultProfileImage() {
        KakaoUserResponse user = new KakaoUserResponse(
                123456789L,
                new KakaoUserResponse.KakaoAccount(
                        new KakaoUserResponse.Profile(
                                "플랜잇사용자",
                                "https://example.com/default-profile.jpg",
                                true
                        )
                )
        );

        assertThat(user.customProfileImageUrl()).isNull();
    }

    @DisplayName("카카오 프로필 이미지 URL이 없으면 사용자 이미지가 없는 것으로 처리한다")
    @Test
    void treatsMissingKakaoProfileImageUrlAsNoImage() {
        KakaoUserResponse user = new KakaoUserResponse(
                123456789L,
                new KakaoUserResponse.KakaoAccount(
                        new KakaoUserResponse.Profile(
                                "플랜잇사용자",
                                null,
                                false
                        )
                )
        );

        assertThat(user.customProfileImageUrl()).isNull();
    }

    @DisplayName("관리자 키로 카카오 연결을 해제한다")
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

    @DisplayName("이미 연결 해제된 카카오 사용자는 성공으로 처리한다")
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
