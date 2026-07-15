package com.smarttrader.v2.context;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.constants.PositioningConstants;
import com.smarttrader.v2.liquidity.LiquidityMapperService;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.TrendDirection;
import com.smarttrader.v2.positioning.CVDCalculatorService;
import com.smarttrader.v2.positioning.FundingMonitorService;
import com.smarttrader.v2.positioning.OIMonitorService;
import com.smarttrader.v2.service.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * Builds an AnalysisContext for a symbol/granularity, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md sections 1A.3 and 1B.5. This is the "Context
 * Builder" the pipeline (Market Data -> Context Builder -> MarketRegimeDetector -> ...)
 * always assumed but never had an implementation for in this codebase.
 *
 * Indicator math (EMA/ATR/support-resistance/consolidation/breakout) is a reasonable,
 * standard implementation, not something any spec version prescribes exactly - flagging
 * this as one place in the pipeline that's an interpretation, not a transcription of a
 * spec rule. cvdDivergence/oiConfirmsUp/oiConfirmsDown are similarly this builder's own
 * reading of V2_TECH_SPEC_v2.5.md section 4's prose rules, since neither the spec nor the
 * plan spells out their exact computation.
 */
@Service
@RequiredArgsConstructor
public class AnalysisContextBuilder {

    /** Need at least this many candles for EMA50 + support/resistance lookback to be meaningful. */
    public static final int MIN_CANDLES = 55;

    private static final int EMA_SHORT_PERIOD = 9;
    private static final int EMA_MEDIUM_PERIOD = 21;
    private static final int EMA_LONG_PERIOD = 50;
    private static final int ATR_PERIOD = 14;
    private static final int SUPPORT_RESISTANCE_LOOKBACK = 20;
    private static final int CONSOLIDATION_LOOKBACK = 5;
    private static final int BREAKOUT_LOOKBACK = 3;
    private static final double VOLUME_SPIKE_LOOKBACK = 20;
    private static final double VOLUME_SPIKE_MULTIPLIER = 1.8;
    private static final double STRONG_CANDLE_BODY_RATIO = 0.6;
    private static final double ATR_SPIKE_MULTIPLIER = 1.5;

    private final ProductService productService;
    private final LiquidityMapperService liquidityMapper;
    private final CVDCalculatorService cvdCalculator;
    private final FundingMonitorService fundingMonitor;
    private final OIMonitorService oiMonitor;

    public AnalysisContext build(String symbol, Granularity granularity) {
        List<Candle> candles = productService.getLiveCandles(symbol, granularity);
        AnalysisContext v22Ctx = buildV22Context(candles);

        LiquidityMap liquidityMap = liquidityMapper.mapLiquidity(symbol, v22Ctx);

        double cvd1m = cvdCalculator.getCVD1m(symbol);
        double cvdSlope5m = cvdCalculator.getCVDSlope5m(symbol);
        boolean cvdDivergence = isPriceNewHigh(candles, PositioningConstants.CVD_DIVERGENCE_LOOKBACK)
                && !cvdCalculator.isNewHigh(symbol, PositioningConstants.CVD_DIVERGENCE_LOOKBACK);

        double oiChange1h = oiMonitor.getOIChange1h(symbol);
        double oiChange24h = oiMonitor.getOIChange24h(symbol);
        boolean priceUp = isPriceUp(candles);
        boolean oiConfirmsUp = priceUp && oiChange1h > 0;
        boolean oiConfirmsDown = !priceUp && oiChange1h > 0;

        return v22Ctx.toBuilder()
                .liquidityMap(liquidityMap)
                .cvd1m(cvd1m)
                .cvdSlope5m(cvdSlope5m)
                .cvdDivergence(cvdDivergence)
                .fundingRateBps(fundingMonitor.getCurrentFundingRateBps(symbol))
                .fundingPercentile30d(fundingMonitor.getFundingPercentile30d(symbol))
                .oiChange1h(oiChange1h)
                .oiChange24h(oiChange24h)
                .oiConfirmsUp(oiConfirmsUp)
                .oiConfirmsDown(oiConfirmsDown)
                .build();
    }

    /** True if the latest close exceeds the highest close of the prior `lookback` candles. */
    private boolean isPriceNewHigh(List<Candle> candles, int lookback) {
        if (candles.size() < 2) {
            return false;
        }
        List<Candle> priorCandles = candles.subList(0, candles.size() - 1);
        List<Candle> window = lastN(priorCandles, lookback);
        double priorHigh = window.stream().mapToDouble(Candle::close).max().orElse(Double.NEGATIVE_INFINITY);
        return candles.get(candles.size() - 1).close() > priorHigh;
    }

    private boolean isPriceUp(List<Candle> candles) {
        if (candles.size() < 2) {
            return false;
        }
        return candles.get(candles.size() - 1).close() > candles.get(candles.size() - 2).close();
    }

    /**
     * @param candles ascending-sorted by timestamp, at least MIN_CANDLES long
     */
    AnalysisContext buildV22Context(List<Candle> candles) {
        if (candles.size() < MIN_CANDLES) {
            throw new IllegalArgumentException(
                    "need at least %d candles to build an AnalysisContext, got %d".formatted(MIN_CANDLES, candles.size()));
        }

        Candle last = candles.get(candles.size() - 1);
        double ema9 = ema(candles, EMA_SHORT_PERIOD);
        double ema21 = ema(candles, EMA_MEDIUM_PERIOD);
        double ema50 = ema(candles, EMA_LONG_PERIOD);
        double atr = atr(candles, ATR_PERIOD);

        List<Candle> priorCandles = candles.subList(0, candles.size() - BREAKOUT_LOOKBACK);
        List<Candle> srWindow = lastN(priorCandles, SUPPORT_RESISTANCE_LOOKBACK);
        double support = srWindow.stream().mapToDouble(Candle::low).min().orElse(last.low());
        double resistance = srWindow.stream().mapToDouble(Candle::high).max().orElse(last.high());

        return AnalysisContext.builder()
                .price(last.close())
                .ema9(ema9)
                .ema21(ema21)
                .ema50(ema50)
                .atr(atr)
                .trendDirection(trendDirection(ema9, ema21, ema50))
                .trendStrength(ema50 == 0 ? 0 : Math.abs(ema9 - ema50) / ema50)
                .nearestSupport(support)
                .nearestResistance(resistance)
                .volumeSpike(isVolumeSpike(candles))
                .strongCandle(isStrongCandle(last))
                .isAboveEMA(last.close() > ema50)
                .recentBreakout(isRecentBreakout(candles, support, resistance))
                .atrSpike(isAtrSpike(last, candles, atr))
                .consolidationRangePercent(consolidationRangePercent(candles))
                .build();
    }

    /** Standard EMA: seeded with an SMA of the first `period` closes, then smoothed forward. */
    private double ema(List<Candle> candles, int period) {
        double multiplier = 2.0 / (period + 1);
        double emaValue = candles.subList(0, period).stream().mapToDouble(Candle::close).average().orElse(0);
        for (int i = period; i < candles.size(); i++) {
            emaValue = candles.get(i).close() * multiplier + emaValue * (1 - multiplier);
        }
        return emaValue;
    }

    /** Average true range over the last `period` candles (simple moving average of true range). */
    private double atr(List<Candle> candles, int period) {
        List<Candle> window = lastN(candles, period + 1);
        double sum = 0;
        int count = 0;
        for (int i = 1; i < window.size(); i++) {
            sum += trueRange(window.get(i), window.get(i - 1));
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private double trueRange(Candle current, Candle previous) {
        return com.smarttrader.v2.util.CandleMath.trueRange(current, previous);
    }

    private boolean isVolumeSpike(List<Candle> candles) {
        List<Candle> window = lastN(candles, (int) VOLUME_SPIKE_LOOKBACK + 1);
        if (window.size() < 2) {
            return false;
        }
        List<Candle> priorWindow = window.subList(0, window.size() - 1);
        double avgVolume = priorWindow.stream().mapToDouble(Candle::volume).average().orElse(0);
        double currentVolume = window.get(window.size() - 1).volume();
        return avgVolume > 0 && currentVolume > avgVolume * VOLUME_SPIKE_MULTIPLIER;
    }

    private boolean isStrongCandle(Candle candle) {
        double range = candle.high() - candle.low();
        if (range <= 0) {
            return false;
        }
        double body = Math.abs(candle.close() - candle.open());
        return body / range >= STRONG_CANDLE_BODY_RATIO;
    }

    private boolean isRecentBreakout(List<Candle> candles, double support, double resistance) {
        List<Candle> recent = lastN(candles, BREAKOUT_LOOKBACK);
        return recent.stream().anyMatch(c -> c.close() > resistance || c.close() < support);
    }

    private boolean isAtrSpike(Candle last, List<Candle> candles, double atr) {
        if (atr <= 0 || candles.size() < 2) {
            return false;
        }
        Candle previous = candles.get(candles.size() - 2);
        return trueRange(last, previous) > atr * ATR_SPIKE_MULTIPLIER;
    }

    private double consolidationRangePercent(List<Candle> candles) {
        List<Candle> window = lastN(candles, CONSOLIDATION_LOOKBACK);
        double high = window.stream().mapToDouble(Candle::high).max().orElse(0);
        double low = window.stream().mapToDouble(Candle::low).min().orElse(0);
        return low == 0 ? 0 : (high - low) / low;
    }

    private TrendDirection trendDirection(double ema9, double ema21, double ema50) {
        if (ema9 > ema21 && ema21 > ema50) {
            return TrendDirection.UP;
        }
        if (ema9 < ema21 && ema21 < ema50) {
            return TrendDirection.DOWN;
        }
        return TrendDirection.SIDEWAYS;
    }

    private static List<Candle> lastN(List<Candle> candles, int n) {
        int from = Math.max(0, candles.size() - n);
        return candles.subList(from, candles.size());
    }
}
