package com.smarttrader.v2.client;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.smarttrader.v2.model.Candle;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;

class CoinbaseClientImplTest {

    private MockWebServer server;
    private CoinbaseClientImpl client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new CoinbaseClientImpl(webClient);
        ReflectionTestUtils.setField(client, "rateLimitMaxRetries", 2);
        ReflectionTestUtils.setField(client, "rateLimitMinBackoff", Duration.ofMillis(10));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String candlesBody() {
        return "{\"candles\":[{\"start\":\"1700000000\",\"low\":\"1.0\",\"high\":\"2.0\","
                + "\"open\":\"1.5\",\"close\":\"1.8\",\"volume\":\"100\"}]}";
    }

    private MockResponse okResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(candlesBody());
    }

    @Test
    void bullish_successfulResponseReturnsCandles() {
        server.enqueue(okResponse());

        List<Candle> result = client.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualTo(1.8);
    }

    @Test
    void bullish_rateLimitedThenSuccessfulRetryReturnsCandles() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(okResponse());

        List<Candle> result = client.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).hasSize(1);
    }

    @Test
    void bearish_persistentRateLimitReturnsEmptyListInsteadOfThrowing() {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(429));
        }

        List<Candle> result = client.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).isEmpty();
    }

    @Test
    void edgeCase_nonRateLimitErrorIsNotRetriedAndPropagates() {
        server.enqueue(new MockResponse().setResponseCode(500));

        Assertions.assertThrows(Exception.class, () -> client.getCandles("BTC-USD", Granularity.ONE_HOUR));
    }
}
