package com.smarttrader.v2.model;

import lombok.Builder;

/**
 * Immutable snapshot of all data required for regime detection and strategy evaluation.
 * Fields per V2_TECH_SPEC.md section 1 and SmartTrader_V2_Production_Spec.md section 3.
 *
 * consolidationRangePercent is not explicitly named in AnalysisContext's field list but is
 * required input for continuation detection ("consolidation range < 2%"); it is carried here
 * as a derived field produced by the (not-yet-implemented) context builder.
 */
@Builder
public record AnalysisContext(
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
        double consolidationRangePercent
) {
}
