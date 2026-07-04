package com.smarttrader.v2.service;

import com.smarttrader.v2.client.CoinbaseClient;
import com.smarttrader.v2.client.CoinbaseClientFactory;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private CoinbaseClientFactory coinbaseClientFactory;

    @Mock
    private CoinbaseClient coinbaseClient;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(coinbaseClientFactory);
    }

    @Test
    void getAllLiveCandlesFetchesEveryTrackedGranularity() {
        when(coinbaseClientFactory.create()).thenReturn(coinbaseClient);
        Candle candle = Candle.builder()
                .timestamp(Instant.now())
                .open(1).high(2).low(0.5).close(1.5).volume(100)
                .build();
        when(coinbaseClient.getCandles(eq("BTC-USD"), any())).thenReturn(List.of(candle));

        Map<Granularity, List<Candle>> result = productService.getAllLiveCandles("BTC-USD");

        assertThat(result).hasSize(Granularity.values().length);
        assertThat(result.values()).allSatisfy(candles -> assertThat(candles).containsExactly(candle));
    }

    @Test
    void getLiveCandlesReturnsCandlesForRequestedGranularity() {
        when(coinbaseClientFactory.create()).thenReturn(coinbaseClient);
        when(coinbaseClient.getCandles("ETH-USD", Granularity.ONE_HOUR)).thenReturn(List.of());

        List<Candle> result = productService.getLiveCandles("ETH-USD", Granularity.ONE_HOUR);

        assertThat(result).isEmpty();
    }
}
