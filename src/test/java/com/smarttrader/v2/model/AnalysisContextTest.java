package com.smarttrader.v2.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0.7 testing gate: "AnalysisContext extends cleanly without breaking existing
 * v2.2 tests" - existing suite passing already covers that; this covers the new
 * v2.5 fields specifically (construction, defaults, null-safety).
 */
class AnalysisContextTest {

    @Test
    void bullish_v22OnlyBuilderStillCompilesAndDefaultsV25FieldsSafely() {
        AnalysisContext ctx = AnalysisContext.builder()
                .price(100.0)
                .trendDirection(TrendDirection.UP)
                .build();

        assertThat(ctx.price()).isEqualTo(100.0);
        assertThat(ctx.cvd1m()).isZero();
        assertThat(ctx.fundingPercentile30d()).isZero();
        assertThat(ctx.liquidityMap()).isNull();
        assertThat(ctx.cascadeActive()).isFalse();
        assertThat(ctx.recentSweeps()).isEmpty();
    }

    @Test
    void bullish_v25FieldsRoundTripThroughTheBuilder() {
        LiquidityPool pool = LiquidityPool.builder().symbol("BTC-USD").type(PoolType.EQH).density(75f).build();
        LiquidityMap map = new LiquidityMap(List.of(pool), 123L);
        OpportunitySweep sweep = OpportunitySweep.builder().symbol("BTC-USD").side("DOWN").density(60f).build();

        AnalysisContext ctx = AnalysisContext.builder()
                .price(100.0)
                .cvd1m(42.0)
                .cvdSlope5m(1.5)
                .cvdDivergence(true)
                .fundingRateBps(12.5)
                .fundingPercentile30d(92)
                .oiChange1h(0.03)
                .oiChange24h(0.18)
                .oiConfirmsUp(true)
                .liquidityMap(map)
                .vwapSession(101.0)
                .vwapSession1SigmaUpper(103.0)
                .vwapSession1SigmaLower(99.0)
                .cascadeActive(true)
                .recentSweeps(List.of(sweep))
                .build();

        assertThat(ctx.cvdDivergence()).isTrue();
        assertThat(ctx.fundingPercentile30d()).isEqualTo(92);
        assertThat(ctx.liquidityMap().pools()).containsExactly(pool);
        assertThat(ctx.recentSweeps()).containsExactly(sweep);
    }

    @Test
    void edgeCase_nullRecentSweepsNormalizesToEmptyList() {
        AnalysisContext ctx = AnalysisContext.builder()
                .price(100.0)
                .recentSweeps(null)
                .build();

        assertThat(ctx.recentSweeps()).isNotNull().isEmpty();
    }
}
