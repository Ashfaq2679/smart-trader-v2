package com.smarttrader.v2.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.client.CoinbaseClient;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.CandleCacheKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleCacheServiceTest {

    @Mock
    private CoinbaseClient coinbaseClient;
    @Mock
    private DomainEventPublisher eventPublisher;

    private Cache<CandleCacheKey, List<Candle>> candleCache;
    private CandleCacheService candleCacheService;

    @BeforeEach
    void setUp() {
        candleCache = Caffeine.newBuilder().build();
        candleCacheService = new CandleCacheService(coinbaseClient, candleCache, eventPublisher);
    }

    private Candle candleAt(long epochSecond, double close) {
        return Candle.builder().timestamp(Instant.ofEpochSecond(epochSecond))
                .open(close).high(close).low(close).close(close).volume(1).build();
    }

    @Test
    void bullish_coldCacheFetchesFullRangeAndPopulatesCache() {
        Candle c1 = candleAt(100, 1.0);
        Candle c2 = candleAt(200, 2.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(List.of(c2, c1));

        List<Candle> result = candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).containsExactly(c1, c2);
        verify(coinbaseClient, never()).getCandles(any(), any(), any());
        assertThat(candleCache.getIfPresent(new CandleCacheKey("BTC-USD", Granularity.ONE_HOUR))).containsExactly(c1, c2);
    }

    @Test
    void bullish_warmCacheFetchesOnlySinceLastCachedTimestampAndMerges() {
        Candle c1 = candleAt(100, 1.0);
        Candle c2 = candleAt(200, 2.0);
        candleCache.put(new CandleCacheKey("BTC-USD", Granularity.ONE_HOUR), List.of(c1, c2));

        Candle c3 = candleAt(300, 3.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR, Instant.ofEpochSecond(201)))
                .thenReturn(List.of(c3));

        List<Candle> result = candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).containsExactly(c1, c2, c3);
        verify(coinbaseClient, times(1)).getCandles("BTC-USD", Granularity.ONE_HOUR, Instant.ofEpochSecond(201));
    }

    @Test
    void bearish_noNewCandlesLeavesCachedSeriesUnchanged() {
        Candle c1 = candleAt(100, 1.0);
        candleCache.put(new CandleCacheKey("BTC-USD", Granularity.ONE_HOUR), List.of(c1));
        when(coinbaseClient.getCandles(eq("BTC-USD"), eq(Granularity.ONE_HOUR), any())).thenReturn(List.of());

        List<Candle> result = candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).containsExactly(c1);
    }

    @Test
    void edgeCase_overlappingTimestampFromFreshFetchDeduplicatesRatherThanDuplicating() {
        Candle staleClose = candleAt(100, 1.0);
        candleCache.put(new CandleCacheKey("BTC-USD", Granularity.ONE_HOUR), List.of(staleClose));

        Candle updatedClose = candleAt(100, 1.5);
        when(coinbaseClient.getCandles(eq("BTC-USD"), eq(Granularity.ONE_HOUR), any())).thenReturn(List.of(updatedClose));

        List<Candle> result = candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).containsExactly(updatedClose);
    }

    @Test
    void edgeCase_differentGranularitiesForSameProductAreCachedIndependently() {
        Candle hourly = candleAt(100, 1.0);
        Candle daily = candleAt(100, 5.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(List.of(hourly));
        when(coinbaseClient.getCandles("BTC-USD", Granularity.FOUR_HOUR)).thenReturn(List.of(daily));

        candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);
        candleCacheService.getCandles("BTC-USD", Granularity.FOUR_HOUR);

        assertThat(candleCache.getIfPresent(new CandleCacheKey("BTC-USD", Granularity.ONE_HOUR))).containsExactly(hourly);
        assertThat(candleCache.getIfPresent(new CandleCacheKey("BTC-USD", Granularity.FOUR_HOUR))).containsExactly(daily);
    }

    @Test
    void edgeCase_repeatedIncrementalFetchesAppendWithoutReorderingOrDroppingHistory() {
        Candle c1 = candleAt(100, 1.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(List.of(c1));
        candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        Candle c2 = candleAt(200, 2.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR, Instant.ofEpochSecond(101))).thenReturn(List.of(c2));
        candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        Candle c3 = candleAt(300, 3.0);
        when(coinbaseClient.getCandles("BTC-USD", Granularity.ONE_HOUR, Instant.ofEpochSecond(201))).thenReturn(List.of(c3));
        List<Candle> result = candleCacheService.getCandles("BTC-USD", Granularity.ONE_HOUR);

        assertThat(result).containsExactly(c1, c2, c3);
    }
}
