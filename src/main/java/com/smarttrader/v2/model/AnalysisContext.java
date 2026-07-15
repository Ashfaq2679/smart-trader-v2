package com.smarttrader.v2.model;

import java.util.List;

import lombok.Builder;

/**
 * Immutable snapshot of all data required for regime detection and strategy evaluation.
 * Fields per V2_TECH_SPEC.md section 1 and SmartTrader_V2_Production_Spec.md section 3,
 * extended per V2_TECH_SPEC_v2.5.md section 1 (Phase 0.1 of V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md).
 *
 * consolidationRangePercent is not explicitly named in AnalysisContext's field list but is
 * required input for continuation detection ("consolidation range < 2%"); it is carried here
 * as a derived field produced by the (not-yet-implemented) context builder.
 *
 * The v2.5 fields below are additive and backward compatible: existing builder call sites
 * still compile unchanged (new fields default to 0/false/null). They are not yet populated
 * or consumed by anything - MarketRegimeDetector/strategies still only read the v2.2 fields
 * until Phase 1A (liquidity), 1B (crowd positioning) and 2 (new strategies) wire them up.
 */
@Builder(toBuilder = true)
public record AnalysisContext(
        // --- v2.2 fields ---
        double price,
        double ema9,
        double ema21,
        double ema50,
        double atr,
        TrendDirection trendDirection,
        double trendStrength,
        double nearestSupport,
        double nearestResistance,
        boolean volumeSpike,
        boolean strongCandle,
        boolean isAboveEMA,
        boolean recentBreakout,
        boolean atrSpike,
        double consolidationRangePercent,

        // --- v2.5 fields: Cumulative Volume Delta (CVD), section 1 / section 4 ---
        /** Cumulative Volume Delta at 1m bar close: running sum(buy-taker volume - sell-taker volume). */
        double cvd1m,
        /** CVD slope over 20 x 5m bars (linear regression). */
        double cvdSlope5m,
        /** True when price made a new high but CVD did not confirm it (trap-watch signal). */
        boolean cvdDivergence,

        // --- v2.5 fields: Crowd Positioning, section 1 / section 4 ---
        /** Perpetual funding rate in basis points (read-only market intelligence; execution stays spot). */
        double fundingRateBps,
        /** 0-100 percentile of current funding within its 30-day distribution. */
        int fundingPercentile30d,
        /** Open Interest (OI) percent change over the last 1 hour. */
        double oiChange1h,
        /** Open Interest (OI) percent change over the last 24 hours. */
        double oiChange24h,
        /** True when price is up AND OI is up (real trend, new money). */
        boolean oiConfirmsUp,
        /** True when price is down AND OI is up (real trend, new short money). */
        boolean oiConfirmsDown,

        // --- v2.5 fields: Liquidity Map, section 3 ---
        LiquidityMap liquidityMap,
        /** Session-anchored Volume-Weighted Average Price (VWAP), UTC session. */
        double vwapSession,
        double vwapSession1SigmaUpper,
        double vwapSession1SigmaLower,

        // --- v2.5 fields: Cascade / Short-Side Engine, section 6 ---
        boolean cascadeActive,
        /** Last 5 swept liquidity pools, most recent first; feeds Sweep-and-Reclaim/SFP/Range-Harvester. */
        List<OpportunitySweep> recentSweeps
) {

    public AnalysisContext {
        if (recentSweeps == null) {
            recentSweeps = List.of();
        }
    }
}
