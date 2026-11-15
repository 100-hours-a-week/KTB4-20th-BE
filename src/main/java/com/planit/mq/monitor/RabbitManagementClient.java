package com.planit.mq.monitor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/** RabbitMQ Management API로 큐 상태를 조회한다. */
@Component
public class RabbitManagementClient {

    private final RestClient restClient;
    private final String virtualHost;
    private final ExecutorService executor;

    public RabbitManagementClient(
            MessageQueueMonitorProperties properties,
            @Qualifier(MessageQueueMonitorConfig.MONITOR_EXECUTOR) ExecutorService executor
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // vhost 이름의 '/'까지 인코딩해야 Management API 경로가 맞는다
        DefaultUriBuilderFactory uriBuilderFactory =
                new DefaultUriBuilderFactory(properties.managementBaseUrl().toString());
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .uriBuilderFactory(uriBuilderFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build();
        this.virtualHost = properties.virtualHost();
        this.executor = executor;
    }

    /**
     * 여러 큐를 큐마다 가상 스레드에서 동시에 조회한다. 조회마다 연결·응답 대기 시간이 걸려 있어
     * 전체 대기 시간도 큐 하나의 대기 시간을 넘지 않는다.
     * 한 큐의 조회가 실패해도 나머지 큐의 결과는 그대로 돌려준다.
     *
     * @return 요청한 순서대로 큐 이름별 조회 결과
     */
    public Map<String, QueueLookup> getQueues(Collection<String> queueNames) {
        Map<String, CompletableFuture<QueueLookup>> futures = new LinkedHashMap<>();
        queueNames.forEach(queueName -> futures.put(queueName, lookupAsync(queueName)));

        Map<String, QueueLookup> lookups = new LinkedHashMap<>();
        futures.forEach((queueName, future) -> lookups.put(queueName, future.join()));
        return lookups;
    }

    private CompletableFuture<QueueLookup> lookupAsync(String queueName) {
        return CompletableFuture.supplyAsync(() -> getQueue(queueName), executor)
                .handle((status, error) -> error == null
                        ? new QueueLookup.Found(status)
                        : new QueueLookup.Failed(unwrap(error)));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * @throws RestClientException 연결·응답 시간 초과, 인증 실패, 없는 큐, 아직 통계가 없는 큐
     */
    public QueueStatus getQueue(String queueName) {
        QueueResponse response = restClient.get()
                .uri("/api/queues/{vhost}/{queue}", virtualHost, queueName)
                .retrieve()
                .body(QueueResponse.class);
        if (response == null || response.messagesReady() == null || response.consumers() == null) {
            throw new RestClientException("큐 상태를 읽지 못함 queue=" + queueName);
        }
        return new QueueStatus(response.messagesReady(), response.consumers());
    }

    /**
     * @param messagesReady 소비자에게 아직 전달하지 않은 메시지 수
     * @param consumers     큐를 구독 중인 소비자 수
     */
    public record QueueStatus(long messagesReady, int consumers) {
    }

    /** 큐 하나의 조회 결과. 실패한 큐는 감시에서 플래그를 바꾸지 않는다. */
    public sealed interface QueueLookup {

        record Found(QueueStatus status) implements QueueLookup {
        }

        record Failed(Throwable cause) implements QueueLookup {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QueueResponse(
            @JsonProperty("messages_ready") Long messagesReady,
            @JsonProperty("consumers") Integer consumers
    ) {
    }
}
