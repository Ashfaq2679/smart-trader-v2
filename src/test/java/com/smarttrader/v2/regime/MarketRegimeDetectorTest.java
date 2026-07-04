package com.smarttrader.v2.regime;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.TrendDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRegimeDetectorTest {

    private final MarketRegimeDetector detector = new MarketRegimeDetector();

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .ema9(99.0)
                .ema21(98.0)
                .ema50(97.0)
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

    // --- Bullish scenarios ---

    @Test
    void detectsBreakoutWhenPriceClearsResistanceWithStrongCandleAndVolumeSpike() {
        AnalysisContext ctx = base()
                .price(112.0)
                .nearestResistance(110.0)
                .strongCandle(true)
                .volumeSpike(true)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.BREAKOUT);
    }

    @Test
    void detectsContinuationAfterBreakoutWithTightConsolidationAboveEma() {
        AnalysisContext ctx = base()
                .recentBreakout(true)
                .isAboveEMA(true)
                .consolidationRangePercent(0.01)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.CONTINUATION);
    }

    @Test
    void detectsPullbackWhenUptrendPriceNearEma50AndAtrStable() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.UP)
                .price(97.5)
                .ema50(97.0)
                .atr(2.0)
                .atrSpike(false)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.PULLBACK);
    }

    @Test
    void detectsPullbackWhenUptrendPriceNearSupport() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.UP)
                .price(91.0)
                .nearestSupport(90.0)
                .ema50(120.0)
                .atr(2.0)
                .atrSpike(false)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.PULLBACK);
    }

    // --- Bearish scenario ---

    @Test
    void detectsPanicOnAtrSpikeAndBreakdownBelowSupport() {
        AnalysisContext ctx = base()
                .price(85.0)
                .nearestSupport(90.0)
                .atrSpike(true)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.PANIC);
    }

    // --- Sideways scenario ---

    @Test
    void detectsDistributionWhenNoOtherRegimeMatches() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.SIDEWAYS)
                .price(100.0)
                .ema50(50.0)
                .nearestSupport(10.0)
                .nearestResistance(200.0)
                .atrSpike(false)
                .recentBreakout(false)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.DISTRIBUTION);
    }

    // --- Edge cases ---

    @Test
    void breakoutTakesPriorityOverPullbackWhenBothConditionsOverlap() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.UP)
                .price(111.0)
                .nearestResistance(110.0)
                .ema50(109.0)
                .atr(5.0)
                .strongCandle(true)
                .volumeSpike(true)
                .atrSpike(false)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.BREAKOUT);
    }

    @Test
    void priceBreaksResistanceButWithoutStrongCandleOrVolumeSpikeIsNotBreakout() {
        AnalysisContext ctx = base()
                .price(112.0)
                .nearestResistance(110.0)
                .strongCandle(false)
                .volumeSpike(false)
                .build();

        assertThat(detector.detect(ctx)).isNotEqualTo(MarketRegime.BREAKOUT);
    }

    @Test
    void consolidationRangeExactlyAtThresholdIsNotContinuation() {
        AnalysisContext ctx = base()
                .recentBreakout(true)
                .isAboveEMA(true)
                .consolidationRangePercent(TradingConstants.CONTINUATION_CONSOLIDATION_THRESHOLD)
                .build();

        assertThat(detector.detect(ctx)).isNotEqualTo(MarketRegime.CONTINUATION);
    }

    @Test
    void priceExactlyAtNearAtrBoundaryCountsAsNearForPullback() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.UP)
                .ema50(100.0)
                .atr(4.0)
                .price(102.0) // exactly atr * NEAR_ATR_MULTIPLIER away
                .nearestSupport(0.0)
                .atrSpike(false)
                .build();

        assertThat(detector.detect(ctx)).isEqualTo(MarketRegime.PULLBACK);
    }

    @Test
    void atrSpikeAlonePreventsPullbackClassification() {
        AnalysisContext ctx = base()
                .trendDirection(TrendDirection.UP)
                .price(97.5)
                .ema50(97.0)
                .atr(2.0)
                .atrSpike(true)
                .build();

        assertThat(detector.detect(ctx)).isNotEqualTo(MarketRegime.PULLBACK);
    }
}
