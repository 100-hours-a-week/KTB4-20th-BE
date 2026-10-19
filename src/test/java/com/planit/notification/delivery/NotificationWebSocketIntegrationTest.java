package com.planit.notification.delivery;

import com.planit.auth.id.UuidV7Generator;
import com.planit.auth.token.JwtTokenProvider;
import com.planit.domain.User;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.dto.NotificationTargetResponse;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationWebSocketIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @LocalServerPort
    private int port;

    @Autowired
    private UuidV7Generator uuidV7Generator;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private StompSession session;
    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User(
                null,
                uuidV7Generator.generate(),
                "알림웹소켓테스트"
        ));
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("notification-stomp-test-");
        taskScheduler.initialize();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.setMessageConverter(
                new JacksonJsonMessageConverter(JsonMapper.builder().build())
        );
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        if (user != null && user.getId() != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @DisplayName("인증 사용자가 본인 알림 destination을 구독하고 알림을 수신한다")
    @Test
    void authenticatedUserReceivesOwnNotification() throws Exception {
        session = connect();
        CompletableFuture<NotificationWebSocketResponse> received =
                new CompletableFuture<>();
        subscribeWithReceipt(received);
        NotificationPublishEvent event = new NotificationPublishEvent(
                user.getPublicId(),
                16001L,
                NotificationType.TRIP_WEATHER_ALERT,
                WeatherCondition.HEAVY_RAIN,
                "여행지에 특이 기상이 예상돼요",
                "여행 기간의 날씨를 확인해 주세요.",
                new NotificationTargetResponse(
                        NotificationTargetType.TRIP,
                        "1001"
                ),
                OffsetDateTime.parse("2026-10-16T10:00:00+09:00")
        );

        applicationEventPublisher.publishEvent(event);

        assertThat(received.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isEqualTo(NotificationWebSocketResponse.from(event));
    }

    private StompSession connect() throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(FRONTEND_ORIGIN);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtTokenProvider.issue(user.getPublicId()).accessToken()
        );
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void subscribeWithReceipt(
            CompletableFuture<NotificationWebSocketResponse> received
    ) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/user/queue/notifications");
        headers.setReceipt(UUID.randomUUID().toString());
        StompSession.Subscription subscription = session.subscribe(
                headers,
                frameHandler(received)
        );
        CompletableFuture<Void> receipt = new CompletableFuture<>();
        subscription.addReceiptTask(() -> receipt.complete(null));
        subscription.addReceiptLostTask(() -> receipt.completeExceptionally(
                new IllegalStateException("알림 구독 receipt를 받지 못했습니다.")
        ));
        receipt.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private StompFrameHandler frameHandler(
            CompletableFuture<NotificationWebSocketResponse> received
    ) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificationWebSocketResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((NotificationWebSocketResponse) payload);
            }
        };
    }
}
