package com.planit.mq.monitor;

import com.planit.mq.monitor.RabbitManagementClient.QueueLookup;
import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.RestClientException;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** 운영과 같이 `planit` vhost와 `monitoring` 태그만 가진 조회 전용 계정으로 조회한다. */
@Testcontainers
class RabbitManagementClientIntegrationTest {

    private static final String VHOST = "planit";
    private static final String MONITOR_USER = "planit-monitor";
    private static final String MONITOR_PASSWORD = "monitor-password";
    private static final String QUEUE = "monitor.test";

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management")
    ).withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "-rabbit collect_statistics_interval 500");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private CachingConnectionFactory connectionFactory;
    private RabbitAdmin admin;

    @BeforeAll
    static void createVhostAndMonitorUser() throws Exception {
        rabbitmqctl("add_vhost", VHOST);
        rabbitmqctl("set_permissions", "-p", VHOST, RABBIT.getAdminUsername(), ".*", ".*", ".*");
        rabbitmqctl("add_user", MONITOR_USER, MONITOR_PASSWORD);
        rabbitmqctl("set_user_tags", MONITOR_USER, "monitoring");
        rabbitmqctl("set_permissions", "-p", VHOST, MONITOR_USER, "^$", "^$", "^$");
    }

    @AfterAll
    static void closeExecutor() {
        EXECUTOR.close();
    }

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setVirtualHost(VHOST);
        admin = new RabbitAdmin(connectionFactory);
        admin.declareQueue(new Queue(QUEUE));
        admin.purgeQueue(QUEUE, false);
    }

    @AfterEach
    void tearDown() {
        admin.deleteQueue(QUEUE);
        connectionFactory.destroy();
    }

    @DisplayName("큐의 대기 메시지 수와 소비자 수를 읽는다")
    @Test
    void readsQueueStatus() throws Exception {
        RabbitManagementClient client = client(URI.create(RABBIT.getHttpUrl()), MONITOR_PASSWORD);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        for (int i = 0; i < 3; i++) {
            template.convertAndSend("", QUEUE, "message-" + i);
        }

        await().atMost(Duration.ofSeconds(15)).ignoreExceptions()
                .untilAsserted(() -> assertThat(client.getQueue(QUEUE)).isEqualTo(new QueueStatus(3, 0)));

        try (Connection connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            channel.basicQos(1);
            channel.basicConsume(QUEUE, false, (tag, delivery) -> { }, tag -> { });

            await().atMost(Duration.ofSeconds(15)).ignoreExceptions()
                    .untilAsserted(() -> assertThat(client.getQueue(QUEUE)).isEqualTo(new QueueStatus(2, 1)));
        }
    }

    @DisplayName("없는 큐, 인증 실패, 연결 실패는 예외로 알린다")
    @Test
    void throwsWhenQueueCannotBeRead() {
        RabbitManagementClient client = client(URI.create(RABBIT.getHttpUrl()), MONITOR_PASSWORD);
        assertThatThrownBy(() -> client.getQueue("missing.queue"))
                .isInstanceOf(RestClientException.class);

        RabbitManagementClient wrongPassword = client(URI.create(RABBIT.getHttpUrl()), "wrong");
        assertThatThrownBy(() -> wrongPassword.getQueue(QUEUE))
                .isInstanceOf(RestClientException.class);

        RabbitManagementClient unreachable = client(URI.create("http://127.0.0.1:1"), MONITOR_PASSWORD);
        assertThatThrownBy(() -> unreachable.getQueue(QUEUE))
                .isInstanceOf(RestClientException.class);
    }

    @DisplayName("여러 큐를 동시에 조회하고, 한 큐의 조회가 실패해도 나머지 결과를 요청 순서대로 돌려준다")
    @Test
    void readsQueuesConcurrently() {
        RabbitManagementClient client = client(URI.create(RABBIT.getHttpUrl()), MONITOR_PASSWORD);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Map<String, QueueLookup> lookups = client.getQueues(List.of(QUEUE, "missing.queue"));

            assertThat(lookups).containsOnlyKeys(QUEUE, "missing.queue");
            assertThat(lookups.keySet()).containsExactly(QUEUE, "missing.queue");
            assertThat(lookups.get(QUEUE)).isEqualTo(new QueueLookup.Found(new QueueStatus(0, 0)));
            assertThat(lookups.get("missing.queue"))
                    .isInstanceOfSatisfying(QueueLookup.Failed.class, failed ->
                            assertThat(failed.cause()).isInstanceOf(RestClientException.class));
        });
    }

    private static RabbitManagementClient client(URI baseUrl, String password) {
        return new RabbitManagementClient(new MessageQueueMonitorProperties(
                baseUrl,
                VHOST,
                MONITOR_USER,
                password,
                Duration.ofMillis(300),
                Duration.ofMillis(500),
                0.95,
                0.50,
                0.90,
                0.50
        ), EXECUTOR);
    }

    private static void rabbitmqctl(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "rabbitmqctl";
        System.arraycopy(args, 0, command, 1, args.length);
        ExecResult result = RABBIT.execInContainer(command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }
}
