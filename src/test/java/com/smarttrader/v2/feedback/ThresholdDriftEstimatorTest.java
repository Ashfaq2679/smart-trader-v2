package com.smarttrader.v2.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThresholdDriftEstimatorTest {

    @Mock
    private ProductService productService;
    @Mock
    private ConfigChangeRepository configChangeRepository;
    @Mock
    private TradingEventPublisher eventPublisher;

    private ThresholdConfigService configService;
    private ThresholdDriftEstimator estimator;

    private ThresholdDriftEstimator build(List<String> symbols, double rangeSize) {
        configService = new ThresholdConfigService();
        estimator = new ThresholdDriftEstimator(productService, configService, configChangeRepository, eventPublisher);
        ReflectionTestUtils.setField(estimator, "trackedSymbols", symbols);
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles(rangeSize));
        return estimator;
    }

    private List<Candle> candles(double rangeSize) {
        List<Candle> candles = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 40; i++) {
            candles.add(Candle.builder()
                    .timestamp(Instant.now().plusSeconds(i * 3600L))
                    .open(price)
                    .high(price + rangeSize)
                    .low(price - rangeSize)
                    .close(price)
                    .volume(1)
                    .build());
        }
        return candles;
    }

    @Test
    void bullish_firstRunSeedsConfigWithoutPublishingAnEvent() {
        build(List.of("BTC-USD"), 1.0).estimateThresholdDrift();

        assertThat(configService.getAtrPercentile90("BTC-USD")).isPresent();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void bearish_largeDriftFromPriorConfigUpdatesAndPublishes() {
        ThresholdDriftEstimator estimator = build(List.of("BTC-USD"), 10.0);
        configService.updateAtrPercentile90("BTC-USD", 1.0);

        estimator.estimateThresholdDrift();

        assertThat(configService.getAtrPercentile90("BTC-USD")).get()
                .satisfies(value -> assertThat(value).isGreaterThan(1.5));
        verify(eventPublisher).publish(any());
        verify(configChangeRepository).save(any());
    }

    @Test
    void sideways_smallDriftFromPriorConfigDoesNotUpdate() {
        ThresholdDriftEstimator estimator = build(List.of("BTC-USD"), 2.0);
        configService.updateAtrPercentile90("BTC-USD", 4.0);

        estimator.estimateThresholdDrift();

        assertThat(configService.getAtrPercentile90("BTC-USD")).contains(4.0);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_notEnoughCandleHistorySkipsGracefully() {
        configService = new ThresholdConfigService();
        estimator = new ThresholdDriftEstimator(productService, configService, configChangeRepository, eventPublisher);
        ReflectionTestUtils.setField(estimator, "trackedSymbols", List.of("BTC-USD"));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(List.of());

        estimator.estimateThresholdDrift();

        assertThat(configService.getAtrPercentile90("BTC-USD")).isEmpty();
    }
}
