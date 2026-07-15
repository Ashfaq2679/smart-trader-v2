package com.smarttrader.v2.strategy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.OpportunitySweep;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import static org.assertj.core.api.Assertions.assertThat;

class SweepReclaimStrategyTest {

    private final SweepReclaimStrategy strategy = new SweepReclaimStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .volumeSpike(true);
    }

    private OpportunitySweep sweep(String side, float density, boolean reclaimed, double level) {
        return OpportunitySweep.builder().symbol("BTC-USD").side(side).density(density)
                .reclaimed(reclaimed).level(BigDecimal.valueOf(level)).build();
    }

    @Test
    void bullish_sweepBelowEqlWithHighDensityAndVolumeProducesALongSignal() {
        // resistance widened so reward/risk clears the 1.5 minimum
        AnalysisContext ctx = base().nearestResistance(120.0).recentSweeps(List.of(sweep("UP", 70f, true, 90.0))).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.stop()).isCloseTo(90.0 - 2.0 * 0.35, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void bearish_sweepAboveEqhWithHighDensityProducesAShortSignal() {
        AnalysisContext ctx = base().nearestSupport(70.0).recentSweeps(List.of(sweep("DOWN", 70f, true, 110.0))).build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(result.stop()).isCloseTo(110.0 + 2.0 * 0.35, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void sideways_noSweepsProducesNoSignal() {
        AnalysisContext ctx = base().recentSweeps(List.of()).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_lowDensityPoolIsRejectedEvenIfReclaimed() {
        AnalysisContext ctx = base().recentSweeps(List.of(sweep("UP", 30f, true, 90.0))).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_notYetReclaimedIsRejected() {
        AnalysisContext ctx = base().recentSweeps(List.of(sweep("UP", 70f, false, 90.0))).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_noVolumeConfirmationIsRejected() {
        AnalysisContext ctx = base().volumeSpike(false).recentSweeps(List.of(sweep("UP", 70f, true, 90.0))).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }
}
