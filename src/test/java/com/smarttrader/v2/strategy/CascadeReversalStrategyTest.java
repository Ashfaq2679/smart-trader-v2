package com.smarttrader.v2.strategy;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeReversalStrategyTest {

    private final CascadeReversalStrategy strategy = new CascadeReversalStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(95.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .nearestSupport(90.0)
                .nearestResistance(120.0)
                .cascadeActive(false)
                .oiChange1h(0.005)
                .cvdSlope5m(1.0)
                .fundingPercentile30d(40);
    }

    @Test
    void bullish_allReversalGatesPassProducesALongSignal() {
        AnalysisContext ctx = base().build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void bearish_stillDuringCascadeNeverEntersRegardlessOfOtherGates() {
        AnalysisContext ctx = base().cascadeActive(true).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_oiNotYetStabilizedBlocksEntry() {
        AnalysisContext ctx = base().oiChange1h(0.05).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_noAbsorptionProxyBlocksEntry() {
        AnalysisContext ctx = base().cvdSlope5m(-1.0).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_priceStillBelowFlushLowBlocksEntry() {
        AnalysisContext ctx = base().price(85.0).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_fundingNotYetResetBlocksEntry() {
        AnalysisContext ctx = base().fundingPercentile30d(60).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }
}
