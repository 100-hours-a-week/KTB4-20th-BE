package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import com.planit.chat.websocket.config.ChatRedisPubSubConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ChatRedisPubSubIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REDIS_COMMAND_TIMEOUT = Duration.ofSeconds(3);
    private static final int REDIS_PORT = 6379;
    private static final int PROXY_PORT = 8666;

    private final Network network = Network.newNetwork();

    @Container
    private final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:8.10.2-alpine")
    )
            .withNetwork(network)
            .withNetworkAliases("redis");

    @Container
    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0")
    ).withNetwork(network);

    private final List<RedisMessageListenerContainer> listenerContainers =
            new ArrayList<>();
    private final List<LettuceConnectionFactory> connectionFactories =
            new ArrayList<>();

    private ChatMessageRedisPublisher publisher;
    private ChannelTopic topic;
    private Proxy redisProxy;

    @BeforeEach
    void setUp() throws IOException {
        redisProxy = new ToxiproxyClient(
                toxiproxy.getHost(),
                toxiproxy.getControlPort()
        ).createProxy(
                "redis",
                "0.0.0.0:" + PROXY_PORT,
                "redis:" + REDIS_PORT
        );
        ChatRedisPubSubConfig config = new ChatRedisPubSubConfig();
        topic = config.chatMessageTopic();
        LettuceConnectionFactory publisherConnectionFactory =
                connectionFactory();
        publisher = new ChatMessageRedisPublisher(
                new StringRedisTemplate(publisherConnectionFactory),
                JsonMapper.builder().build(),
                topic
        );
    }

    @AfterEach
    void tearDown() {
        listenerContainers.forEach(RedisMessageListenerContainer::stop);
        connectionFactories.forEach(LettuceConnectionFactory::destroy);
        network.close();
    }

    @DisplayName("발행한 채팅 이벤트를 두 애플리케이션 Listener가 각각 한 번 수신한다")
    @Test
    void deliversPublishedEventOnceToEachApplicationInstance() throws Exception {
        BlockingQueue<Message<?>> firstInstanceMessages = startSubscriber();
        BlockingQueue<Message<?>> secondInstanceMessages = startSubscriber();
        ChatMessageCreatedEvent event = event("10001");

        publisher.publish(event);

        assertReceivedOnce(firstInstanceMessages, event);
        assertReceivedOnce(secondInstanceMessages, event);
    }

    @DisplayName("Redis 연결이 끊겼다가 복구되면 재구독하여 새 채팅 이벤트를 수신한다")
    @Test
    void resubscribesAfterRedisConnectionRecovers() throws Exception {
        BlockingQueue<Message<?>> messages = startSubscriber();
        ChatMessageCreatedEvent beforeOutage = event("10001");
        publisher.publish(beforeOutage);
        assertReceivedOnce(messages, beforeOutage);

        disconnectRedisProxy();
        awaitPublishFailure(event("10002"));
        reconnectRedisProxy();
        awaitSubscriptionRecovery(messages);

        ChatMessageCreatedEvent afterRecovery = event("10003");
        publisher.publish(afterRecovery);

        assertReceivedOnce(messages, afterRecovery);
    }

    private BlockingQueue<Message<?>> startSubscriber() {
        BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();
        MessageChannel outputChannel = (message, timeout) -> messages.offer(message);
        ChatMessageRedisSubscriber subscriber = new ChatMessageRedisSubscriber(
                JsonMapper.builder().build(),
                new SimpMessagingTemplate(outputChannel)
        );
        LettuceConnectionFactory connectionFactory = connectionFactory();
        RedisMessageListenerContainer listenerContainer =
                new ChatRedisPubSubConfig().chatMessageListenerContainer(
                        connectionFactory,
                        subscriber,
                        topic
                );
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        listenerContainers.add(listenerContainer);
        return messages;
    }

    private LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration redisConfiguration =
                new RedisStandaloneConfiguration(
                        toxiproxy.getHost(),
                        toxiproxy.getMappedPort(PROXY_PORT)
                );
        LettuceClientConfiguration clientConfiguration =
                LettuceClientConfiguration.builder()
                        .commandTimeout(REDIS_COMMAND_TIMEOUT)
                        .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(
                        redisConfiguration,
                        clientConfiguration
                );
        connectionFactory.afterPropertiesSet();
        connectionFactories.add(connectionFactory);
        return connectionFactory;
    }

    private void disconnectRedisProxy() throws IOException {
        redisProxy.disable();
    }

    private void reconnectRedisProxy() throws IOException {
        redisProxy.enable();
    }

    private void awaitPublishFailure(ChatMessageCreatedEvent event) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                publisher.publish(event);
            } catch (RuntimeException expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Redis 중단 후 발행 실패가 확인되지 않았습니다.");
    }

    private void awaitSubscriptionRecovery(
            BlockingQueue<Message<?>> messages
    ) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        long probeSequence = 20_000L;
        while (System.nanoTime() < deadline) {
            ChatMessageCreatedEvent probe = event(Long.toString(probeSequence++));
            try {
                publisher.publish(probe);
                Message<?> received = messages.poll(250, TimeUnit.MILLISECONDS);
                if (received != null) {
                    messages.clear();
                    return;
                }
            } catch (RuntimeException ignored) {
                // Publisher and listener connections recover independently.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Redis 복구 후 Listener가 다시 구독하지 못했습니다.");
    }

    private void assertReceivedOnce(
            BlockingQueue<Message<?>> messages,
            ChatMessageCreatedEvent expectedEvent
    ) throws Exception {
        Message<?> received = messages.poll(
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        assertThat(received).isNotNull();
        assertThat(received.getPayload()).isEqualTo(expectedEvent);
        assertThat(SimpMessageHeaderAccessor.getDestination(received.getHeaders()))
                .isEqualTo("/topic/regional-chat-rooms/3001/messages");
        assertThat(messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    private ChatMessageCreatedEvent event(String messageId) {
        ChatMessageItemResponse message = new ChatMessageItemResponse(
                messageId,
                "019b1234-5678-7000-8000-123456789abc",
                "TEXT",
                "메시지",
                null,
                new ChatSenderResponse(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e",
                        "플랜잇사용자",
                        null
                ),
                Instant.parse("2026-10-19T07:30:00Z")
        );
        return ChatMessageCreatedEvent.from("3001", message);
    }
}
