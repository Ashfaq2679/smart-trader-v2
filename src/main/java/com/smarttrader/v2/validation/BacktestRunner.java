package com.smarttrader.v2.validation;

/**
 * Runs a historical backtest for a strategy/symbol, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.1/4.3 ("run backtest: >= 500 trades,
 * expectancy > 0, param sensitivity, Monte Carlo").
 *
 * No implementation of this interface exists in this codebase. A real backtest engine
 * needs a candle-replay pipeline that reconstructs a historical AnalysisContext per past
 * candle (indicators, liquidity map, CVD, funding/OI as they stood at that point in time)
 * and re-runs strategies/RiskEngine against it - that pipeline doesn't exist here (per
 * CLAUDE.md's roadmap, "Backtesting" is still a later phase; AnalysisContextBuilder only
 * builds a *current* live snapshot). Rather than fabricate expectancy/param-sensitivity/
 * Monte-Carlo numbers that would let ValidationPipelineService.validateResearch()
 * silently wave a strategy into SHADOW on invented confidence, StrategyStateManager
 * callers must supply a real BacktestRunner bean once the replay engine exists; until
 * then, wire NoBacktestDataRunner, whose result always fails the promotion gates.
 */
public interface BacktestRunner {

    BacktestResult run(String strategyName, String symbol);
}
