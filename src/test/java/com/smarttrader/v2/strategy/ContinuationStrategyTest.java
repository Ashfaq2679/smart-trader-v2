package com.smarttrader.v2.strategy;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;
import org.junit.jupiter.api.Test;

import static com.smarttrader.v2.strategy.TestOffsets.DOUBLE_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

class ContinuationStrategyTest {

    private final ContinuationStrategy strategy = new ContinuationStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .ema9(99.0).ema21(98.0).ema50(97.0)
                .atr(2.0)
                .trendDirection(TrendDirection.UP)
                .trendStrength(1.0)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .volumeSpike(false)
                .strongCandle(false)
                .isAboveEMA(true)
                .recentBreakout(true)
                .atrSpike(false)
                .consolidationRangePercent(0.01);
    }

    @Test
    void bullish_validContinuationProducesLongSignal() {
        var result = strategy.evaluate(base().atr(3.0).build());

        assertThat(result.valid()).isTrue();
        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.target()).isCloseTo(100.0 + 3.0 * TradingConstants.CONTINUATION_TARGET_ATR, DOUBLE_OFFSET);
        assertThat(result.stop()).isCloseTo(Math.min(97.0, 100.0 * (1 - 0.01)), DOUBLE_OFFSET);
    }

    @Test
    void bearish_noRecentBreakoutNeverProducesSignal() {
        var result = strategy.evaluate(base()
                .recentBreakout(false)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.direction()).isEqualTo(TradeDirection.NONE);
    }

    @Test
    void sideways_priceBelowEmaInvalidatesContinuation() {
        var result = strategy.evaluate(base()
                .isAboveEMA(false)
                .build());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void edgeCase_consolidationRangeAtThresholdIsInvalid() {
        var result = strategy.evaluate(base()
                .consolidationRangePercent(TradingConstants.CONTINUATION_CONSOLIDATION_THRESHOLD)
                .build());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void edgeCase_insufficientRiskRewardInvalidatesOtherwiseValidSetup() {
        var result = strategy.evaluate(base()
                .atr(0.1)
                .build());

        assertThat(result.riskReward()).isLessThan(2.0);
        assertThat(result.valid()).isFalse();
    }
}
