package com.smarttrader.v2.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Immutable snapshot of all data required for regime detection and strategy evaluation.
 * Fields per V2_TECH_SPEC_v1.1.md section 1 (supersedes V2_TECH_SPEC.md section 1).
 *
 * bidPrice/askPrice/spread and lastCandleCloseTime/dataLatencyMs support execution realism
 * and data-integrity checks (v1.1 sections 6 and 10); they are not yet consumed by the
 * regime detector or strategies, which still key off price/candle-derived fields.
 */
@Builder
public record AnalysisContext(
        double price,
        double bidPrice,
        double askPrice,
        double spread,
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
        Instant lastCandleCloseTime,
        long dataLatencyMs
) {
}
