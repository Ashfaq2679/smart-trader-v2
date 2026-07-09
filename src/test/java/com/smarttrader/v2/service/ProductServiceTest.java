package com.smarttrader.v2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.CoinDocument;
import com.smarttrader.v2.repository.ProductsRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private CandleCacheService candleCacheService;
    @Mock
    private ProductsRepository productsRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(candleCacheService, productsRepository);
    }

    @Test
    void getAllLiveCandlesFetchesEveryTrackedGranularity() {
        System.out.println("Running getAllLiveCandlesFetchesEveryTrackedGranularity test...");
        Candle candle = Candle.builder()
                .timestamp(Instant.now())
                .open(1).high(2).low(0.5).close(1.5).volume(100)
                .build();
        when(candleCacheService.getCandles(eq("BTC-USD"), any())).thenReturn(List.of(candle));

        Map<Granularity, List<Candle>> result = productService.getAllLiveCandles("BTC-USD");

        assertThat(result).hasSize(Granularity.values().length);
        assertThat(result.values()).allSatisfy(candles -> assertThat(candles).containsExactly(candle));
    }

    @Test
    void getLiveCandlesReturnsCandlesForRequestedGranularity() {
        System.out.println("Running getLiveCandlesReturnsCandlesForRequestedGranularity test...");
        when(candleCacheService.getCandles("ETH-USD", Granularity.ONE_HOUR)).thenReturn(List.of());

        List<Candle> result = productService.getLiveCandles("ETH-USD", Granularity.ONE_HOUR);

        assertThat(result).isEmpty();
    }

    private CoinDocument coin(String productId) {
        CoinDocument coin = new CoinDocument();
        coin.setProductId(productId);
        return coin;
    }

    @Test
    void bullish_findProductIdToProcessReturnsAllProductsWhenNoneAreIgnored() {
        when(productsRepository.findAll()).thenReturn(List.of(coin("BTC-USD"), coin("ETH-USD")));

        List<String> result = productService.findProductIdToProcess();

        assertThat(result).containsExactlyInAnyOrder("BTC-USD", "ETH-USD");
    }

    @Test
    void bearish_findProductIdToProcessExcludesConfiguredIgnoreList() {
        ReflectionTestUtils.setField(productService, "ignoreProductIds", List.of("BTC-USDC"));
        when(productsRepository.findAll()).thenReturn(List.of(coin("BTC-USD"), coin("BTC-USDC")));

        List<String> result = productService.findProductIdToProcess();

        assertThat(result).containsExactly("BTC-USD");
    }

    @Test
    void edgeCase_findProductIdToProcessSkipsDocumentsWithNullProductId() {
        when(productsRepository.findAll()).thenReturn(List.of(coin("BTC-USD"), coin(null)));

        List<String> result = productService.findProductIdToProcess();

        assertThat(result).containsExactly("BTC-USD");
    }
}
