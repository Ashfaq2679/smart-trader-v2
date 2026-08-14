package com.smarttrader.v2.model;

/**
 * Validation-pipeline stage for a (strategy, symbol) pair, per
 * V2_TECH_SPEC_v2.5.md section 12 / V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.
 * Ordinal order matters: RESEARCH < SHADOW < MICRO_LIVE < FULL is the promotion path,
 * and StrategyStateManager.demote() validates a target stage is strictly lower than the
 * current one using this ordering.
 */
public enum StrategyStage {
    /** Signals fire (and are sirened) but never place orders, live or paper. */
    RESEARCH,
    /** Signals are logged (ShadowModeService) against real market data; no orders. */
    SHADOW,
    /** Real orders at reduced size, per section 12 stage gates. */
    MICRO_LIVE,
    /** Full-size live trading. */
    FULL
}
