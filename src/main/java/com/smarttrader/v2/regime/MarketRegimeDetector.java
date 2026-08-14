package com.smarttrader.v2.regime;

import com.smarttrader.v2.constants.PlaybookConstants;
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
 * and the detectMarketRegime priority order in SmartTrader_Skills_Spec.md (v2.2 chain):
 *
 *   IF breakout                    -> BREAKOUT
 *   ELSE IF continuation           -> CONTINUATION
 *   ELSE IF trend + pullback       -> PULLBACK
 *   ELSE IF atrSpike + breakdown   -> PANIC
 *   ELSE                           -> DISTRIBUTION
 *
 * V2_TECH_SPEC_v2.5.md section 2 (Playbook Matrix) adds 5 regimes, checked ahead of the
 * v2.2 chain per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 2.7 - EXCEPT RANGE, which
 * sits between PANIC and DISTRIBUTION. RANGE's own definition (trendStrength < 0.25) is
 * true by default whenever a caller doesn't explicitly set trendStrength, so checking it
 * before the v2.2 chain would pre-empt breakout/continuation/pullback signals that are
 * more specific and should win; isRange() also requires price to sit inside a plausible,
 * bounded support/resistance band precisely so it doesn't fire on wide/undefined structure
 * that really means "no read" (DISTRIBUTION), not "a real range".
 */
@Slf4j
@Component
public class MarketRegimeDetector {

    public MarketRegime detect(AnalysisContext ctx) {
        MarketRegime regime;
        if (isSqueezeLong(ctx)) {
            regime = MarketRegime.SQUEEZE_LONG;
        } else if (isSqueezeShort(ctx)) {
            regime = MarketRegime.SQUEEZE_SHORT;
        } else if (isNewsShock(ctx)) {
            regime = MarketRegime.NEWS_SHOCK;
        } else if (isChop(ctx)) {
            regime = MarketRegime.CHOP;
        } else if (isBreakout(ctx)) {
            regime = MarketRegime.BREAKOUT;
        } else if (isContinuation(ctx)) {
            regime = MarketRegime.CONTINUATION;
        } else if (isPullback(ctx)) {
            regime = MarketRegime.PULLBACK;
        } else if (isPanic(ctx)) {
            regime = MarketRegime.PANIC;
        } else if (isRange(ctx)) {
            regime = MarketRegime.RANGE;
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

    /** SQUEEZE_LONG: funding crowded long AND OI growing fast, per section 2. */
    private boolean isSqueezeLong(AnalysisContext ctx) {
        return ctx.fundingPercentile30d() >= PlaybookConstants.SQUEEZE_LONG_FUNDING_PERCENTILE
                && ctx.oiChange24h() > PlaybookConstants.SQUEEZE_OI_CHANGE_THRESHOLD;
    }

    /** SQUEEZE_SHORT: funding crowded short AND OI growing fast, per section 2. */
    private boolean isSqueezeShort(AnalysisContext ctx) {
        return ctx.fundingPercentile30d() <= PlaybookConstants.SQUEEZE_SHORT_FUNDING_PERCENTILE
                && ctx.oiChange24h() > PlaybookConstants.SQUEEZE_OI_CHANGE_THRESHOLD;
    }

    /**
     * NEWS_SHOCK: section 2 describes this as a market-alarm/flash-crash condition,
     * fed by a macro event calendar that isn't wired up (section 11: "eventBlackout
     * never true" without one). cascadeActive is the closest real signal already on
     * AnalysisContext for "something extreme is happening right now".
     */
    private boolean isNewsShock(AnalysisContext ctx) {
        return ctx.cascadeActive();
    }

    /**
     * CHOP: "toxic tape: spread > 8bps OR band < 1.5 x ATR" (section 2). AnalysisContext
     * has no L2/spread field, so this can't be evaluated yet - always false, per section
     * 11's degraded-mode rule (a strategy/regime may never silently assume an unavailable
     * input; here that means CHOP simply never fires rather than guessing).
     */
    private boolean isChop(AnalysisContext ctx) {
        return false;
    }

    /**
     * RANGE: contained inside a bounded support/resistance band, weak trend, no ATR spike,
     * not mid-breakout/continuation. See the class javadoc for why this sits after PANIC
     * rather than "highest priority" as section 2's table literally implies.
     */
    private boolean isRange(AnalysisContext ctx) {
        double atr = ctx.atr();
        if (atr <= 0) {
            return false;
        }
        double bandWidth = ctx.nearestResistance() - ctx.nearestSupport();
        boolean bandWithinBounds = bandWidth >= PlaybookConstants.RANGE_MIN_BAND_WIDTH_ATR_MULTIPLE * atr
                && bandWidth <= PlaybookConstants.RANGE_MAX_BAND_WIDTH_ATR_MULTIPLE * atr;
        boolean priceInsideBand = ctx.price() >= ctx.nearestSupport() && ctx.price() <= ctx.nearestResistance();

        return ctx.trendDirection() == TrendDirection.SIDEWAYS
                && !ctx.recentBreakout()
                && !ctx.atrSpike()
                && ctx.trendStrength() < PlaybookConstants.RANGE_TREND_STRENGTH_THRESHOLD
                && priceInsideBand
                && bandWithinBounds;
    }
}
