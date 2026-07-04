package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;
import org.junit.jupiter.api.Test;

import static com.smarttrader.v2.strategy.TestOffsets.DOUBLE_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

class PullbackStrategyTest {

    private final PullbackStrategy strategy = new PullbackStrategy();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .ema9(99.0).ema21(98.0).ema50(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.UP)
                .trendStrength(1.0)
                .nearestSupport(90.0)
                .nearestResistance(120.0)
                .volumeSpike(false)
                .strongCandle(false)
                .isAboveEMA(true)
                .recentBreakout(false)
                .atrSpike(false)
                .consolidationRangePercent(0.05);
    }

    @Test
    void bullish_validPullbackNearEma50ProducesLongSignal() {
        var result = strategy.evaluate(base().nearestResistance(130.0).build());

        assertThat(result.valid()).isTrue();
        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(result.stop()).isCloseTo(90.0 - 2.0 * 0.5, DOUBLE_OFFSET);
        assertThat(result.target()).isCloseTo(130.0, DOUBLE_OFFSET);
        assertThat(result.riskReward()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void bullish_validPullbackNearSupportProducesLongSignal() {
        var result = strategy.evaluate(base()
                .price(91.0)
                .ema50(150.0)
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.direction()).isEqualTo(TradeDirection.LONG);
    }

    @Test
    void bearish_downtrendNeverProducesSignal() {
        var result = strategy.evaluate(base()
                .trendDirection(TrendDirection.DOWN)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.direction()).isEqualTo(TradeDirection.NONE);
    }

    @Test
    void sideways_trendNeverProducesSignal() {
        var result = strategy.evaluate(base()
                .trendDirection(TrendDirection.SIDEWAYS)
                .build());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void edgeCase_priceFarFromEmaAndSupportIsInvalid() {
        var result = strategy.evaluate(base()
                .price(105.0)
                .ema50(100.0)
                .nearestSupport(90.0)
                .atr(2.0)
                .build());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void edgeCase_insufficientRiskRewardInvalidatesOtherwiseValidSetup() {
        var result = strategy.evaluate(base()
                .nearestResistance(101.0)
                .build());

        assertThat(result.riskReward()).isLessThan(2.0);
        assertThat(result.valid()).isFalse();
    }
}
