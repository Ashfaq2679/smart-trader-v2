package com.smarttrader.v2.validation;

/**
 * Live-trading metrics for a (strategy, symbol) pair, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.3 (validateMicroLive). Derived from
 * TradeOutcomeRepository, so it's real once outcomes are recorded, and honestly empty
 * ("no evidence", not "assume fine") until they are.
 */
public record LiveMetrics(int fills, double slippage, double expectancy) {

    public static LiveMetrics empty() {
        return new LiveMetrics(0, Double.MAX_VALUE, 0.0);
    }
}
