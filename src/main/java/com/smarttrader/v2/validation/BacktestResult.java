package com.smarttrader.v2.validation;

/**
 * Result of a backtest run, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.1/4.3.
 * See BacktestRunner's javadoc for why this codebase can currently only produce the
 * fails-safe "insufficient evidence" result rather than real backtest numbers.
 */
public record BacktestResult(int trades, double expectancy, double paramSensitivity, double monteCarloRoR) {

    public static BacktestResult noData() {
        return new BacktestResult(0, 0.0, 0.0, 1.0);
    }
}
