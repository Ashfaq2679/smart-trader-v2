package com.smarttrader.v2.service;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ProductService end-to-end through the real Spring wiring
 * (CoinbaseClientImpl -> coinbaseWebClient bean) against a stubbed Coinbase
 * HTTP server, rather than mocking CoinbaseClient directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductServiceIntegrationTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private ProductService productService;

    @DynamicPropertySource
    static void coinbaseApiProperties(DynamicPropertyRegistry registry) {
        registry.add("coinbase.api.base-url", () -> mockWebServer.url("/").toString());
        // Test-only key so CredentialEncryptionService's fail-fast check doesn't block
        // context startup; this test never exercises encryption/decryption itself.
        registry.add("CREDENTIAL_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    private static String candleResponseJson(String startEpochSeconds) {
        return """
                {"candles":[{"start":"%s","low":"90.0","high":"110.0","open":"95.0","close":"105.0","volume":"1000.0"}]}
                """.formatted(startEpochSeconds);
    }

    @Test
    void getAllLiveCandlesFetchesEveryGranularityFromTheRealHttpClient() {
        for (int i = 0; i < Granularity.values().length; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(candleResponseJson(String.valueOf(1_700_000_000 + i))));
        }

        System.out.println("Running getAllLiveCandlesFetchesEveryGranularityFromTheRealHttpClient test...");
        Map<Granularity, List<Candle>> result = productService.getAllLiveCandles("BTC-USD");

        assertThat(result).hasSize(Granularity.values().length);
        for (Granularity granularity : Granularity.values()) {
            List<Candle> candles = result.get(granularity);
            assertThat(candles).hasSize(1);
            assertThat(candles.get(0).open()).isEqualTo(95.0);
            assertThat(candles.get(0).close()).isEqualTo(105.0);
        }
    }

    @Test
    void getLiveCandlesRequestsTheCorrectPathAndGranularityQueryParam() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(candleResponseJson("1700000000")));

        List<Candle> candles = productService.getLiveCandles("ETH-USD", Granularity.ONE_HOUR);

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).volume()).isEqualTo(1000.0);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/api/v3/brokerage/market/products/ETH-USD/candles");
        assertThat(request.getPath()).contains("granularity=ONE_HOUR");
    }

    @Test
    void edgeCase_emptyCandlesArrayReturnsEmptyList() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"candles\":[]}"));

        List<Candle> candles = productService.getLiveCandles("BTC-USD", Granularity.FIVE_MINUTE);

        assertThat(candles).isEmpty();
    }
}
