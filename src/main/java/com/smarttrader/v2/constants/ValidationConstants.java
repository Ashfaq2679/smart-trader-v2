package com.smarttrader.v2.constants;

/**
 * Constants for the validation pipeline (V2_TECH_SPEC_v2.5.md section 12 /
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4), "validated: false" per section 12
 * until real sensitivity analysis, same as the other v2.5 subsystem constants classes.
 */
public final class ValidationConstants {

    /** validateResearch gate: minimum backtested trade count before SHADOW promotion. */
    public static final int RESEARCH_MIN_BACKTEST_TRADES = 500;

    /** validateResearch gate: max acceptable Monte Carlo risk-of-ruin. */
    public static final double RESEARCH_MAX_MONTE_CARLO_ROR = 0.01;

    /** validateShadow gate: minimum age in shadow mode before MICRO_LIVE promotion. */
    public static final int SHADOW_MIN_AGE_DAYS = 28;

    /** validateShadow gate: minimum logged shadow signals before MICRO_LIVE promotion. */
    public static final int SHADOW_MIN_SIGNAL_COUNT = 100;

    /** validateShadow gate: minimum signal-distribution match vs backtest (section 12: +/-20%). */
    public static final double SHADOW_MIN_DISTRIBUTION_MATCH = 0.8;

    /** validateMicroLive gate: minimum live fills before FULL promotion. */
    public static final int MICRO_LIVE_MIN_FILLS = 100;

    /** validateMicroLive gate: max acceptable slippage multiple vs modeled. */
    public static final double MICRO_LIVE_MAX_SLIPPAGE_MULTIPLE = 1.5;

    /** Demotion window: number of most recent trade outcomes examined per rule. */
    public static final int DEMOTION_LOOKBACK_TRADES = 20;

    /** Demotion trigger: average slippage multiple above this demotes to SHADOW. */
    public static final double DEMOTION_SLIPPAGE_MULTIPLE = 2.0;

    private ValidationConstants() {
    }
}
