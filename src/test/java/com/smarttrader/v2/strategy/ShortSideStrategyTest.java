package com.smarttrader.v2.strategy;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import static org.assertj.core.api.Assertions.assertThat;

class ShortSideStrategyTest {

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.DOWN)
                .oiConfirmsDown(true)
                .cvdSlope5m(-1.0);
    }

    @Test
    void bullish_setupDetectedAndExecutedWhenVenueCanShort() {
        ShortSideStrategy strategy = new ShortSideStrategy(true);
        AnalysisContext ctx = base().build();

        var result = strategy.evaluate(ctx);

        assertThat(result.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void bearish_setupDetectedButNotExecutableWhenVenueCannotShort() {
        ShortSideStrategy strategy = new ShortSideStrategy(false);
        AnalysisContext ctx = base().build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void sideways_upTrendNeverTriggersAShortSetup() {
        ShortSideStrategy strategy = new ShortSideStrategy(true);
        AnalysisContext ctx = base().trendDirection(TrendDirection.UP).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_downtrendWithoutOiConfirmationDoesNotTrigger() {
        ShortSideStrategy strategy = new ShortSideStrategy(true);
        AnalysisContext ctx = base().oiConfirmsDown(false).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_positiveCvdSlopeDoesNotTrigger() {
        ShortSideStrategy strategy = new ShortSideStrategy(true);
        AnalysisContext ctx = base().cvdSlope5m(1.0).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }
}
