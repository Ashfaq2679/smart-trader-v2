package com.smarttrader.v2.strategy;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import static org.assertj.core.api.Assertions.assertThat;

class RangeHarvesterStrategyTest {

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .cascadeActive(false);
    }

    @Test
    void bullish_touchOfRangeLowBuysTheDip() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(false);
        AnalysisContext ctx = base().price(90.5).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.valid()).isTrue();
        assertThat(result.target()).isEqualTo(100.0); // mid-range
    }

    @Test
    void bearish_touchOfRangeHighSellsTheTopWhenVenueCanShort() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(true);
        AnalysisContext ctx = base().price(109.5).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void edgeCase_touchOfRangeHighIsInvalidWhenVenueCannotShort() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(false);
        AnalysisContext ctx = base().price(109.5).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void sideways_midRangeProducesNoSignal() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(false);
        AnalysisContext ctx = base().price(100.0).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_cascadeActiveNeverHarvestsEvenAtAnEdge() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(false);
        AnalysisContext ctx = base().price(90.5).cascadeActive(true).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_bandTooNarrowForAtrProducesNoSignal() {
        RangeHarvesterStrategy strategy = new RangeHarvesterStrategy(false);
        // band width = 3, but 2 x ATR(2.0) = 4 -> too narrow
        AnalysisContext ctx = base().nearestSupport(97.0).nearestResistance(100.0).price(97.2).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }
}
