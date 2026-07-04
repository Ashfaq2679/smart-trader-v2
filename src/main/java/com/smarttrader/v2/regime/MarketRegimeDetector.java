package com.smarttrader.v2.regime;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.TrendDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Classifies the current MarketRegime from an AnalysisContext.
 *
 * Detection rules per V2_TECH_SPEC.md section 2, SmartTrader_V2_Production_Spec.md section 4,
 * and the detectMarketRegime priority order in SmartTrader_Skills_Spec.md:
 *
 *   IF breakout                    -> BREAKOUT
 *   ELSE IF continuation           -> CONTINUATION
 *   ELSE IF trend + pullback       -> PULLBACK
 *   ELSE IF atrSpike + breakdown   -> PANIC
 *   ELSE                           -> DISTRIBUTION
 */
@Slf4j
@Component
public class MarketRegimeDetector {

    public MarketRegime detect(AnalysisContext ctx) {
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

        log.info("marketRegimeDetector regime={} price={} trend={}", regime, ctx.price(), ctx.trendDirection());
        return regime;
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
}
