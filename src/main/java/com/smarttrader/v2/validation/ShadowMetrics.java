package com.smarttrader.v2.validation;

import java.time.Duration;

/**
 * Shadow-mode metrics for a (strategy, symbol) pair, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.5/4.3.
 *
 * distributionMatch is always 0.0 in this codebase: computing "±20% of backtest" requires
 * a persisted reference distribution from a real backtest run, and BacktestRunner (Phase
 * 4.3) has no candle-replay engine to produce one yet (see BacktestRunner's javadoc).
 * Returning an invented "looks fine" value here would let a strategy promote to
 * MICRO_LIVE on faked confidence; failing safe at 0.0 keeps that gate closed until real
 * backtest data exists to compare against.
 */
public record ShadowMetrics(Duration age, int signalCount, double distributionMatch) {

    public static ShadowMetrics empty() {
        return new ShadowMetrics(Duration.ZERO, 0, 0.0);
    }
}
