package com.smarttrader.v2.strategy;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import static org.assertj.core.api.Assertions.assertThat;

class SFPReversalStrategyTest {

    private final SFPReversalStrategy strategy = new SFPReversalStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .fundingPercentile30d(50);
    }

    @Test
    void bullish_atSupportWithCvdDivergenceProducesALongSignal() {
        AnalysisContext ctx = base().price(90.5).cvdDivergence(true).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void bearish_atResistanceWithCvdDivergenceProducesAShortSignal() {
        AnalysisContext ctx = base().price(109.5).cvdDivergence(true).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void bullish_atSupportWithExtremeLowFundingIsSufficientConfluence() {
        AnalysisContext ctx = base().price(90.5).fundingPercentile30d(5).build();

        assertThat(strategy.evaluate(ctx).valid()).isTrue();
    }

    @Test
    void sideways_midRangeWithNoEdgeProximityProducesNoSignal() {
        AnalysisContext ctx = base().price(100.0).cvdDivergence(true).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_atSupportWithoutAnyConfluenceProducesNoSignal() {
        AnalysisContext ctx = base().price(90.5).cvdDivergence(false).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_zeroAtrProducesNoSignal() {
        AnalysisContext ctx = base().atr(0.0).price(90.0).cvdDivergence(true).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }
}
