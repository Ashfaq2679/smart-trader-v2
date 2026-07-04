package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;
import org.junit.jupiter.api.Test;

import static com.smarttrader.v2.strategy.TestOffsets.DOUBLE_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

class BreakoutStrategyTest {

    private final BreakoutStrategy strategy = new BreakoutStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .ema9(99.0).ema21(98.0).ema50(97.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .trendStrength(0.0)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .volumeSpike(false)
                .strongCandle(false)
                .isAboveEMA(false)
                .recentBreakout(false)
                .atrSpike(false)
                .consolidationRangePercent(0.05);
    }

    @Test
    void bullish_validBreakoutUpProducesLongSignalWithFixedAtrStopAndTarget() {
        var result = strategy.evaluate(base()
                .price(112.0)
                .strongCandle(true)
                .volumeSpike(true)
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.stop()).isCloseTo(112.0 - 2.0 * 1.2, DOUBLE_OFFSET);
        assertThat(result.target()).isCloseTo(112.0 + 2.0 * 3.0, DOUBLE_OFFSET);
        assertThat(result.riskReward()).isCloseTo(3.0 / 1.2, DOUBLE_OFFSET);
    }

    @Test
    void bearish_validBreakdownProducesShortSignal() {
        var result = strategy.evaluate(base()
                .price(85.0)
                .strongCandle(true)
                .volumeSpike(true)
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(result.stop()).isCloseTo(85.0 + 2.0 * 1.2, DOUBLE_OFFSET);
        assertThat(result.target()).isCloseTo(85.0 - 2.0 * 3.0, DOUBLE_OFFSET);
    }

    @Test
    void sideways_priceInsideRangeNeverBreaksOut() {
        var result = strategy.evaluate(base()
                .price(100.0)
                .strongCandle(true)
                .volumeSpike(true)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.direction()).isEqualTo(TradeDirection.NONE);
    }

    @Test
    void edgeCase_priceBreaksResistanceButNoVolumeSpikeIsInvalid() {
        var result = strategy.evaluate(base()
                .price(112.0)
                .strongCandle(true)
                .volumeSpike(false)
                .build());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void edgeCase_priceBreaksResistanceButNoStrongCandleIsInvalid() {
        var result = strategy.evaluate(base()
                .price(112.0)
                .strongCandle(false)
                .volumeSpike(true)
                .build());

        assertThat(result.valid()).isFalse();
    }
}
