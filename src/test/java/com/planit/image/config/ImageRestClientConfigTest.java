package com.planit.image.config;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageRestClientConfigTest {

    @Test
    void timesOutWhenProfileImageResponseIsTooSlow() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/profile", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            ImageProperties properties = new ImageProperties(
                    URI.create("https://example.com/default-profile.svg"),
                    "test-bucket",
                    "ap-northeast-2",
                    Duration.ofMinutes(5),
                    5_242_880,
                    Duration.ofMillis(100),
                    Duration.ofMillis(100)
            );
            RestClient restClient = new ImageRestClientConfig()
                    .kakaoProfileImageRestClient(properties);
            String url = "http://localhost:"
                    + server.getAddress().getPort()
                    + "/profile";

            assertThatThrownBy(() -> restClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            server.stop(0);
        }
    }
}
