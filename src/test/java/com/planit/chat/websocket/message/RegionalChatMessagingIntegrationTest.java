package com.planit.chat.websocket.message;

import com.planit.auth.id.UuidV7Generator;
import com.planit.auth.token.JwtTokenProvider;
import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageResultEvent;
import com.planit.chat.dto.ChatMessageSendRequest;
import com.planit.domain.ChatPolicyStatus;
import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.ChatPolicyConsent;
import com.planit.domain.ImagePurpose;
import com.planit.domain.RegionalChatRoom;
import com.planit.domain.RegionalChatRoomMember;
import com.planit.domain.User;
import com.planit.repository.ChatMessageRepository;
import com.planit.repository.ChatPolicyConsentRepository;
import com.planit.repository.ChatPolicyVersionRepository;
import com.planit.repository.ImageFileRepository;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegionalChatMessagingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Long ROOM_ID = 1L;
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
    private ImageFileRepository imageFileRepository;

    @Autowired
    private RegionalChatRoomRepository roomRepository;

    @Autowired
    private RegionalChatRoomMemberRepository memberRepository;

    @Autowired
    private ChatPolicyVersionRepository policyVersionRepository;

    @Autowired
    private ChatPolicyConsentRepository policyConsentRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private StompSession session;
    private User user;
    private RegionalChatRoomMember membership;

    @BeforeEach
    void setUp() {
        user = createUser("STOMP통합테스트");
        RegionalChatRoom room = roomRepository.findById(ROOM_ID).orElseThrow();
        ChatPolicyVersion policy = policyVersionRepository
                .findByStatus(ChatPolicyStatus.ACTIVE)
                .orElseThrow();
        membership = memberRepository.save(new RegionalChatRoomMember(user, room));
        policyConsentRepository.save(new ChatPolicyConsent(user, policy));

        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("stomp-integration-test-");
        taskScheduler.initialize();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
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
            Long userId = user.getId();
            jdbcTemplate.update("""
                    DELETE textMessage
                    FROM text_chat_messages textMessage
                    JOIN chat_messages message
                      ON message.id = textMessage.chat_message_id
                    WHERE message.sender_user_id = ?
                    """, userId);
            jdbcTemplate.update(
                    "DELETE FROM chat_messages WHERE sender_user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM chat_policy_consents WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM regional_chat_room_members WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void sendsPersistsAndReceivesTextMessageOverStomp() throws Exception {
        session = connect(accessToken(user));
        CompletableFuture<ChatMessageResultEvent> resultEvent = new CompletableFuture<>();
        CompletableFuture<ChatMessageCreatedEvent> createdEvent = new CompletableFuture<>();

        subscribe(
                "/user/queue/chat-events",
                ChatMessageResultEvent.class,
                resultEvent
        );
        subscribeWithReceipt(
                roomDestination(),
                ChatMessageCreatedEvent.class,
                createdEvent
        );

        UUID clientMessageId = uuidV7Generator.generate();
        session.send(
                sendDestination(),
                new ChatMessageSendRequest(
                        clientMessageId,
                        "TEXT",
                        "  STOMP   실제 송수신  ",
                        null
                )
        );

        ChatMessageResultEvent result = resultEvent.get(
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        ChatMessageCreatedEvent created = createdEvent.get(
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.clientMessageId()).isEqualTo(clientMessageId.toString());
        assertThat(created.eventType()).isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(created.data().clientMessageId()).isEqualTo(clientMessageId.toString());
        assertThat(created.data().text()).isEqualTo("STOMP 실제 송수신");
        assertThat(messageRepository.findBySenderAndClientMessageId(
                user,
                clientMessageId
        )).isPresent();
    }

    @Test
    void rejectsConnectWithInvalidAccessToken() throws Exception {
        CompletableFuture<JsonNode> errorFrame = new CompletableFuture<>();
        StompSessionHandlerAdapter handler = errorCapturingHandler(errorFrame);

        stompClient.connectAsync(
                webSocketUri(),
                handshakeHeaders(),
                connectHeaders("invalid-access-token"),
                handler
        );

        JsonNode error = errorFrame.get(
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        assertThat(error.get("code").asText()).isEqualTo("INVALID_ACCESS_TOKEN");
    }

    @Test
    void rejectsRoomSubscriptionForNonMember() throws Exception {
        membership.leave();
        memberRepository.saveAndFlush(membership);
        CompletableFuture<JsonNode> errorFrame = new CompletableFuture<>();
        session = connect(accessToken(user), errorCapturingHandler(errorFrame));

        StompHeaders headers = new StompHeaders();
        headers.setDestination(roomDestination());
        headers.setReceipt("non-member-subscribe");
        session.subscribe(
                headers,
                frameHandler(ChatMessageCreatedEvent.class, new CompletableFuture<>())
        );

        JsonNode error = errorFrame.get(
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        assertThat(error.get("code").asText())
                .isEqualTo("REGIONAL_CHAT_MEMBER_REQUIRED");
    }

    private User createUser(String username) {
        return userRepository.save(new User(
                imageFileRepository.findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                ).orElseThrow(),
                uuidV7Generator.generate(),
                username
        ));
    }

    private String accessToken(User targetUser) {
        return jwtTokenProvider.issue(targetUser.getPublicId()).accessToken();
    }

    private StompSession connect(String accessToken) throws Exception {
        return connect(accessToken, new StompSessionHandlerAdapter() {
        });
    }

    private StompSession connect(
            String accessToken,
            StompSessionHandlerAdapter sessionHandler
    ) throws Exception {
        return stompClient.connectAsync(
                webSocketUri(),
                handshakeHeaders(),
                connectHeaders(accessToken),
                sessionHandler
        ).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private WebSocketHttpHeaders handshakeHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(FRONTEND_ORIGIN);
        return headers;
    }

    private StompHeaders connectHeaders(String accessToken) {
        StompHeaders headers = new StompHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return headers;
    }

    private <T> void subscribeWithReceipt(
            String destination,
            Class<T> payloadType,
            CompletableFuture<T> event
    ) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setReceipt(UUID.randomUUID().toString());
        StompSession.Subscription subscription = session.subscribe(
                headers,
                frameHandler(payloadType, event)
        );
        CompletableFuture<Void> receipt = new CompletableFuture<>();
        subscription.addReceiptTask(() -> receipt.complete(null));
        subscription.addReceiptLostTask(() -> receipt.completeExceptionally(
                new IllegalStateException("STOMP 구독 receipt를 받지 못했습니다.")
        ));
        receipt.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private <T> void subscribe(
            String destination,
            Class<T> payloadType,
            CompletableFuture<T> event
    ) {
        session.subscribe(destination, frameHandler(payloadType, event));
    }

    private <T> StompFrameHandler frameHandler(
            Class<T> payloadType,
            CompletableFuture<T> event
    ) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                event.complete(payloadType.cast(payload));
            }
        };
    }

    private StompSessionHandlerAdapter errorCapturingHandler(
            CompletableFuture<JsonNode> errorFrame
    ) {
        return new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorFrame.complete((JsonNode) payload);
            }

            @Override
            public void handleTransportError(
                    StompSession stompSession,
                    Throwable exception
            ) {
                if (!errorFrame.isDone()) {
                    errorFrame.completeExceptionally(exception);
                }
            }
        };
    }

    private String webSocketUri() {
        return "ws://localhost:" + port + "/ws";
    }

    private String roomDestination() {
        return "/topic/regional-chat-rooms/" + ROOM_ID + "/messages";
    }

    private String sendDestination() {
        return "/app/regional-chat-rooms/" + ROOM_ID + "/messages";
    }
}
