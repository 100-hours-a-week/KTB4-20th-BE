package com.planit.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRestClientConfigTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void stopsWaitingWhenAiResponseExceedsReadTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        AiProperties properties = new AiProperties(
                URI.create("http://localhost:" + server.getAddress().getPort()),
                "test-internal-token",
                Duration.ofSeconds(1),
                Duration.ofMillis(50)
        );
        RestClient client = new AiRestClientConfig().aiRestClient(properties);

        assertThatThrownBy(() -> client.get()
                .uri(properties.baseUrl().resolve("/slow"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
    }
}
