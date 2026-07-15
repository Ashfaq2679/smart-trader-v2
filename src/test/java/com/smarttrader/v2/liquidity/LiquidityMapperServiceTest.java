package com.smarttrader.v2.liquidity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.LiquidityPool;
import com.smarttrader.v2.model.PoolType;
import com.smarttrader.v2.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiquidityMapperServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private LiquidityPoolRepository repository;

    private Cache<String, List<LiquidityPool>> cache;
    private LiquidityMapperService service;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        service = new LiquidityMapperService(productService, repository, cache);
    }

    private Candle candle(long epochSecond, double open, double high, double low, double close, double volume) {
        return Candle.builder().timestamp(Instant.ofEpochSecond(epochSecond))
                .open(open).high(high).low(low).close(close).volume(volume).build();
    }

    /** Two EQH fractal candidates ~1 apart (106, 105) and one lone EQL fractal (94). */
    private List<Candle> fractalCandles() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(0, 97, 100, 95, 98, 10));
        candles.add(candle(60, 99, 106, 99, 100, 10));  // EQH fractal @106
        candles.add(candle(120, 100, 101, 96, 99, 10));
        candles.add(candle(180, 99, 100, 94, 97, 10));  // EQL fractal @94
        candles.add(candle(240, 97, 105, 98, 99, 10));  // EQH fractal @105
        candles.add(candle(300, 99, 102, 97, 100, 10));
        return candles;
    }

    // --- Fractal detection ---

    @Test
    void bullish_detectFractalsFindsHighAndLowFractals() {
        List<LiquidityPool> fractals = service.detectFractals("BTC-USD", fractalCandles(), 10.0);

        assertThat(fractals).hasSize(3);
        assertThat(fractals).filteredOn(p -> p.getType() == PoolType.EQH)
                .extracting(p -> p.getLevel().doubleValue())
                .containsExactlyInAnyOrder(106.0, 105.0);
        assertThat(fractals).filteredOn(p -> p.getType() == PoolType.EQL)
                .extracting(p -> p.getLevel().doubleValue())
                .containsExactly(94.0);
    }

    @Test
    void edgeCase_tooFewCandlesProducesNoFractals() {
        List<LiquidityPool> fractals = service.detectFractals("BTC-USD", List.of(candle(0, 1, 2, 0, 1, 1)), 10.0);

        assertThat(fractals).isEmpty();
    }

    // --- Clustering (Equal Highs/Lows require >= 2 touches within 0.15 * ATR) ---

    @Test
    void bullish_clusterExtremesGroupsNearbyFractalsIntoAnEqualHighsPool() {
        List<LiquidityPool> fractals = service.detectFractals("BTC-USD", fractalCandles(), 10.0);
        double threshold = 10.0 * 0.15; // ATR=10 -> 1.5

        List<LiquidityPool> clustered = service.clusterExtremes("BTC-USD", fractals, List.of(), threshold);

        assertThat(clustered).hasSize(1);
        LiquidityPool eqh = clustered.get(0);
        assertThat(eqh.getType()).isEqualTo(PoolType.EQH);
        assertThat(eqh.getTouches()).isEqualTo(2);
        assertThat(eqh.getLevel().doubleValue()).isCloseTo(105.5, offset(0.01));
    }

    @Test
    void bearish_loneFractalWithoutASecondTouchIsDropped() {
        List<LiquidityPool> fractals = service.detectFractals("BTC-USD", fractalCandles(), 10.0);

        List<LiquidityPool> clustered = service.clusterExtremes("BTC-USD", fractals, List.of(), 1.5);

        assertThat(clustered).noneMatch(p -> p.getType() == PoolType.EQL);
    }

    // --- Density decay ---

    @Test
    void bullish_densityIsFullStrengthOnTheDayThePoolIsCreated() {
        Instant now = Instant.parse("2026-01-05T00:00:00Z");
        LiquidityPool pool = LiquidityPool.builder().touches(3).createdAtNs(toNanos(now)).build();

        service.updateDensity(List.of(pool), now);

        assertThat(pool.getDensity()).isCloseTo(30.0f, offset(0.01f)); // 3 touches * 1.0 decay * 10
    }

    @Test
    void bearish_densityDecaysByLambdaPerDayOfAge() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = createdAt.plusSeconds(2 * 24 * 3600); // 2 days later
        LiquidityPool pool = LiquidityPool.builder().touches(3).createdAtNs(toNanos(createdAt)).build();

        service.updateDensity(List.of(pool), now);

        // 3 touches * 0.8^2 decay * 10 = 19.2
        assertThat(pool.getDensity()).isCloseTo(19.2f, offset(0.1f));
    }

    @Test
    void edgeCase_densityIsCappedAtOneHundred() {
        Instant now = Instant.now();
        LiquidityPool pool = LiquidityPool.builder().touches(1000).createdAtNs(toNanos(now)).build();

        service.updateDensity(List.of(pool), now);

        assertThat(pool.getDensity()).isEqualTo(100.0f);
    }

    // --- Merge / de-dupe against cached pools ---

    @Test
    void bullish_mergeBumpsTouchesOnAnExistingNearbyPoolInsteadOfDuplicating() {
        LiquidityPool cached = LiquidityPool.builder().id("existing").symbol("BTC-USD")
                .level(BigDecimal.valueOf(105.0)).type(PoolType.EQH).touches(2).lastTouchedNs(0).build();
        LiquidityPool fresh = LiquidityPool.builder().symbol("BTC-USD")
                .level(BigDecimal.valueOf(105.4)).type(PoolType.EQH).touches(1).lastTouchedNs(999).build();

        List<LiquidityPool> merged = service.merge(new ArrayList<>(List.of(cached)), List.of(fresh), List.of(), 1.5);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getTouches()).isEqualTo(3);
        assertThat(merged.get(0).getLastTouchedNs()).isEqualTo(999);
    }

    @Test
    void edgeCase_mergeAppendsAsANewPoolWhenNoNearbyMatchExists() {
        LiquidityPool cached = LiquidityPool.builder().id("existing").symbol("BTC-USD")
                .level(BigDecimal.valueOf(50.0)).type(PoolType.EQL).touches(2).build();
        LiquidityPool fresh = LiquidityPool.builder().symbol("BTC-USD")
                .level(BigDecimal.valueOf(105.0)).type(PoolType.EQH).touches(2).build();

        List<LiquidityPool> merged = service.merge(new ArrayList<>(List.of(cached)), List.of(fresh), List.of(), 1.5);

        assertThat(merged).hasSize(2);
    }

    // --- End-to-end ---

    @Test
    void bullish_mapLiquidityPersistsAndCachesDiscoveredPools() {
        when(productService.getLiveCandles("BTC-USD", Granularity.FIFTEEN_MINUTE)).thenReturn(fractalCandles());
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(fractalCandles());
        when(repository.findActivePoolsBySymbol("BTC-USD")).thenReturn(List.of());

        AnalysisContext ctx = AnalysisContext.builder().atr(10.0).build();
        LiquidityMap result = service.mapLiquidity("BTC-USD", ctx);

        assertThat(result.pools()).isNotEmpty();
        verify(repository).saveAll(any());
        assertThat(cache.getIfPresent("BTC-USD")).isNotNull();
    }

    @Test
    void edgeCase_secondCallUsesTheCacheInsteadOfHittingTheRepositoryAgain() {
        when(productService.getLiveCandles(any(), any())).thenReturn(fractalCandles());
        when(repository.findActivePoolsBySymbol("BTC-USD")).thenReturn(List.of());
        AnalysisContext ctx = AnalysisContext.builder().atr(10.0).build();

        service.mapLiquidity("BTC-USD", ctx);
        service.mapLiquidity("BTC-USD", ctx);

        verify(repository).findActivePoolsBySymbol("BTC-USD"); // only once, cache warm after first call
    }

    private static long toNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
