package com.smarttrader.v2.backtest;

import com.smarttrader.v2.model.AnalysisContext;
import lombok.Builder;

import java.time.Instant;

/**
 * One point in a historical replay timeline (V2_TECH_SPEC_v1.1.md section 12,
 * "Historical replay"). A pre-built AnalysisContext per tick, rather than raw candles:
 * this codebase doesn't yet have a candle -> indicator ("Context Builder") pipeline, so
 * the backtest harness takes already-computed AnalysisContext snapshots as input.
 */
@Builder
public record BacktestTick(
        String productId,
        AnalysisContext context,
        double price,
        Instant timestamp
) {
}
