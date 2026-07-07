package com.smarttrader.v2.regime;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.MarketRegimeResult;
import com.smarttrader.v2.model.TrendDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Classifies the current MarketRegime from an AnalysisContext.
 *
 * Detection rules per V2_TECH_SPEC_v1.1.md section 2 (Pullback/Breakout/Continuation),
 * extended with the PANIC/DISTRIBUTION priority tail from SmartTrader_V2_Production_Spec.md
 * section 4 and SmartTrader_Skills_Spec.md:
 *
 *   IF breakout                    -> BREAKOUT
 *   ELSE IF continuation           -> CONTINUATION
 *   ELSE IF trend + pullback       -> PULLBACK
 *   ELSE IF atrSpike + breakdown   -> PANIC
 *   ELSE                           -> DISTRIBUTION
 *
 * Per v1.1 section 2, every detection returns a MarketRegimeResult carrying a confidence
 * in [0, 1]. The spec does not define the confidence formula, so each regime uses a
 * documented heuristic: a base confidence for satisfying the boolean gate, plus a bonus
 * for how strongly the underlying signal exceeds its threshold (distance beyond a
 * level in ATR units, or tightness of consolidation/proximity relative to its threshold).
 */
@Slf4j
@Component
public class MarketRegimeDetector {

    private static final double BASE_CONFIDENCE = 0.6;
    private static final double BONUS_CONFIDENCE = 0.4;
    private static final double DISTRIBUTION_CONFIDENCE = 0.3;

    public MarketRegimeResult detect(AnalysisContext ctx) {
        MarketRegime regime;
        if (isBreakout(ctx)) {
            regime = MarketRegime.BREAKOUT;
        } else if (isContinuation(ctx)) {
            regime = MarketRegime.CONTINUATION;
        } else if (isPullback(ctx)) {
            regime = MarketRegime.PULLBACK;
        } else if (isPanic(ctx)) {
            regime = MarketRegime.PANIC;
        } else {
            regime = MarketRegime.DISTRIBUTION;
        }

        double confidence = confidenceFor(regime, ctx);
        MarketRegimeResult result = MarketRegimeResult.builder().regime(regime).confidence(confidence).build();

        log.info("marketRegimeDetector regime={} confidence={} price={} trend={}",
                regime, confidence, ctx.price(), ctx.trendDirection());
        return result;
    }

    /**
     * Breakout: price > resistance OR price < support, AND strongCandle AND volumeSpike.
     */
    private boolean isBreakout(AnalysisContext ctx) {
        boolean priceBreak = ctx.price() > ctx.nearestResistance() || ctx.price() < ctx.nearestSupport();
        return priceBreak && ctx.strongCandle() && ctx.volumeSpike();
    }

    /**
     * Continuation: recentBreakout AND price holds above EMA AND consolidation range < 2%.
     */
    private boolean isContinuation(AnalysisContext ctx) {
        return ctx.recentBreakout()
                && ctx.isAboveEMA()
                && ctx.consolidationRangePercent() < TradingConstants.CONTINUATION_CONSOLIDATION_THRESHOLD;
    }

    /**
     * Pullback: trend == UP AND price near EMA50 or support AND ATR is stable (no atrSpike).
     */
    private boolean isPullback(AnalysisContext ctx) {
        return ctx.trendDirection() == TrendDirection.UP
                && isNearEmaOrSupport(ctx)
                && !ctx.atrSpike();
    }

    /**
     * Panic: ATR spike AND breakdown below support.
     */
    private boolean isPanic(AnalysisContext ctx) {
        return ctx.atrSpike() && ctx.price() < ctx.nearestSupport();
    }

    private boolean isNearEmaOrSupport(AnalysisContext ctx) {
        double atr = ctx.atr();
        boolean nearEma = Math.abs(ctx.price() - ctx.ema50()) <= atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        boolean nearSupport = Math.abs(ctx.price() - ctx.nearestSupport()) <= atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        return nearEma || nearSupport;
    }

    private double confidenceFor(MarketRegime regime, AnalysisContext ctx) {
        return switch (regime) {
            case BREAKOUT -> breakoutConfidence(ctx);
            case CONTINUATION -> continuationConfidence(ctx);
            case PULLBACK -> pullbackConfidence(ctx);
            case PANIC -> panicConfidence(ctx);
            case DISTRIBUTION -> DISTRIBUTION_CONFIDENCE;
        };
    }

    private double breakoutConfidence(AnalysisContext ctx) {
        double level = ctx.price() > ctx.nearestResistance() ? ctx.nearestResistance() : ctx.nearestSupport();
        return atrDistanceConfidence(ctx.price(), level, ctx.atr());
    }

    private double panicConfidence(AnalysisContext ctx) {
        return atrDistanceConfidence(ctx.price(), ctx.nearestSupport(), ctx.atr());
    }

    private double atrDistanceConfidence(double price, double level, double atr) {
        if (atr <= 0) {
            return BASE_CONFIDENCE;
        }
        double distanceAtr = Math.abs(price - level) / atr;
        return clip(BASE_CONFIDENCE + Math.min(distanceAtr, 1.0) * BONUS_CONFIDENCE);
    }

    private double continuationConfidence(AnalysisContext ctx) {
        double threshold = TradingConstants.CONTINUATION_CONSOLIDATION_THRESHOLD;
        double tightnessRatio = 1 - (ctx.consolidationRangePercent() / threshold);
        return clip(BASE_CONFIDENCE + clip(tightnessRatio) * BONUS_CONFIDENCE);
    }

    private double pullbackConfidence(AnalysisContext ctx) {
        double atr = ctx.atr();
        if (atr <= 0) {
            return BASE_CONFIDENCE;
        }
        double nearestDistance = Math.min(Math.abs(ctx.price() - ctx.ema50()), Math.abs(ctx.price() - ctx.nearestSupport()));
        double nearThreshold = atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        double proximityRatio = 1 - (nearestDistance / nearThreshold);
        return clip(BASE_CONFIDENCE + clip(proximityRatio) * BONUS_CONFIDENCE);
    }

    private double clip(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
