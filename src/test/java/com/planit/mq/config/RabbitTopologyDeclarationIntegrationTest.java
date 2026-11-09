package com.planit.mq.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
class RabbitTopologyDeclarationIntegrationTest {

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management")
    );

    private AnnotationConfigApplicationContext context;
    private RabbitAdmin admin;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(CachingConnectionFactory.class, () -> {
            CachingConnectionFactory factory = new CachingConnectionFactory(
                    RABBIT.getHost(),
                    RABBIT.getAmqpPort()
            );
            factory.setUsername(RABBIT.getAdminUsername());
            factory.setPassword(RABBIT.getAdminPassword());
            return factory;
        });
        context.registerBean(RabbitAdmin.class, () -> new RabbitAdmin(
                context.getBean(CachingConnectionFactory.class)
        ));
        context.register(RabbitTopologyConfig.class);
        context.refresh();
        admin = context.getBean(RabbitAdmin.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @DisplayName("RabbitMQ가 선언한 exchange, 큐, binding을 인자 그대로 생성한다")
    @Test
    void declaresTopologyOnBroker() throws Exception {
        admin.initialize();

        List<String> queues = lines("rabbitmqctl", "list_queues", "-q", "--no-table-headers", "name", "type");
        assertThat(queues).hasSize(20).allMatch(line -> line.endsWith("quorum"));

        List<String> exchanges = lines("rabbitmqctl", "list_exchanges", "-q", "--no-table-headers", "name", "type");
        assertThat(exchanges).contains(
                "ai.request\tdirect",
                "ai.retry\tdirect",
                "ai.dlx\tdirect",
                "ai.result\tdirect"
        );

        List<String> bindings = lines(
                "rabbitmqctl", "list_bindings", "-q", "--no-table-headers",
                "source_name", "destination_name", "routing_key"
        );
        assertThat(bindings).contains(
                "ai.request\tai.schedule-generation.task\tai-schedule-generation",
                "ai.retry\tai.photomission-verification.retry.20s\tai-photomission-verification.retry.3",
                "ai.dlx\tai.photomission-generation.dlq\tai-photomission-generation.dlq",
                "ai.result\tai.result.task\tai-result"
        );
    }

    @DisplayName("이미 선언된 토폴로지를 다시 선언해도 실패하지 않는다")
    @Test
    void redeclaresIdempotently() {
        admin.initialize();

        assertThatCode(() -> admin.initialize()).doesNotThrowAnyException();
        assertThat(admin.getQueueInfo("ai.result.task")).isNotNull();
    }

    private List<String> lines(String... command) throws Exception {
        ExecResult result = RABBIT.execInContainer(command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return Arrays.stream(result.getStdout().split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
